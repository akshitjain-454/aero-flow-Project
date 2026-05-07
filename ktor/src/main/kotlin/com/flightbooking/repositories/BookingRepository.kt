package com.flightbooking.repositories

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.models.Booking
import com.flightbooking.models.BookingInfo
import com.flightbooking.models.FlightInfoRequestSummary
import com.flightbooking.models.Passenger
import com.flightbooking.models.Payment
import com.flightbooking.models.Seat
import com.flightbooking.models.SeatAvailability
import com.flightbooking.models.SelectedSeat
import com.flightbooking.models.TicketAssignment
import com.flightbooking.models.TicketInfo
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightInfoRequestTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val TRANSACTION_ID_LENGTH = 10
private const val BOOKING_REFERENCE_LENGTH = 8
private const val LOYALTY_POINT_REFUND_SCALE = 2
private val LOYALTY_POINT_DIVISOR = BigDecimal("100")
private const val PRICE_SCALE = 2
private val PRICE_ROUNDING_MODE = RoundingMode.HALF_UP
private val DEFAULT_PRICE_MULTIPLIER = BigDecimal("1.0")
private val ECONOMY_PRICE_MULTIPLIER = BigDecimal("1.0")
private val BUSINESS_PRICE_MULTIPLIER = BigDecimal("1.75")
private val FIRST_CLASS_PRICE_MULTIPLIER = BigDecimal("3.0")
private val SAME_DAY_PRICE_MULTIPLIER = BigDecimal("1.75")
private val THREE_DAY_PRICE_MULTIPLIER = BigDecimal("1.5")
private val ONE_WEEK_PRICE_MULTIPLIER = BigDecimal("1.25")
private val TWO_WEEK_PRICE_MULTIPLIER = BigDecimal("1.15")
private val FOUR_WEEK_PRICE_MULTIPLIER = BigDecimal("1.1")
private const val SHORT_TERM_BOOKING_DAYS = 3L
private const val ONE_WEEK_BOOKING_WEEKS = 1L
private const val TWO_WEEK_BOOKING_WEEKS = 2L
private const val FOUR_WEEK_BOOKING_WEEKS = 4L
private const val NO_LOYALTY_POINTS = 0
private const val ZERO = 0

/**
 * Handles booking lifecycle operations, payments, seat selection, and customer flight information requests.
 *
 * This repository is responsible for persisting booking data and producing booking-related summaries.
 */
class BookingRepository {
    /**
     * Creates a new booking record for a user and flight.
     *
     * @param userId The ID of the user making the booking.
     * @param flightId The ID of the outbound flight.
     * @param returnFlightId The optional ID of a return flight.
     * @return The newly created booking.
     */
    fun createBooking(
        userId: Int,
        flightId: Int,
        returnFlightId: Int?,
    ): Booking =
        transaction {
            val now = LocalDateTime.now()
            val reference = generateBookingReference()

            val bookingId =
                BookingTable.insert {
                    it[bookingReference] = reference
                    it[BookingTable.userId] = userId
                    it[BookingTable.flightId] = flightId
                    it[BookingTable.returnFlightId] = returnFlightId
                    it[status] = BookingStatus.CREATED
                    it[createdAt] = now
                } get BookingTable.id

            Booking(
                id = bookingId,
                bookingReference = reference,
                userId = userId,
                flightId = flightId,
                returnFlightId = returnFlightId,
                status = BookingStatus.CREATED,
                createdAt = now,
            )
        }

    /**
     * Creates a completed payment record for a booking.
     *
     * @param bookingId The ID of the booking being paid for.
     * @param amount The payment amount.
     * @param paymentMethod The payment method used.
     * @return The completed payment record.
     */
    fun createPayment(
        bookingId: Int,
        amount: BigDecimal,
        paymentMethod: PaymentMethod,
    ): Payment =
        transaction {
            val now = LocalDateTime.now()
            val transactionId = UUID.randomUUID().toString().replace("-", "").substring(0, TRANSACTION_ID_LENGTH).uppercase()

            val paymentId =
                PaymentTable.insert {
                    it[PaymentTable.bookingId] = bookingId
                    it[PaymentTable.amount] = amount
                    it[PaymentTable.paymentStatus] = PaymentStatus.COMPLETED
                    it[PaymentTable.paymentMethod] = paymentMethod
                    it[PaymentTable.transactionId] = transactionId
                    it[createdAt] = now
                } get PaymentTable.id

            Payment(
                id = paymentId,
                bookingId = bookingId,
                amount = amount,
                paymentStatus = PaymentStatus.COMPLETED,
                paymentMethod = paymentMethod,
                transactionId = transactionId,
                createdAt = now,
                refundAmount = null,
                refundDate = null,
            )
        }

    /**
     * Retrieves the current loyalty points balance for a user.
     *
     * @param userId The ID of the user whose points are retrieved.
     * @return The current loyalty points balance.
     * @throws IllegalStateException if the user record cannot be found.
     */
    fun getLoyaltyPointsByUserId(userId: Int): Int =
        transaction {
            val currentPoints =
                UserTable
                    .select { UserTable.id eq userId }
                    .map { it[UserTable.loyaltyPoints] }
                    .singleOrNull() ?: throw IllegalStateException("User points not found")

            return@transaction currentPoints
        }

    /**
     * Retrieves the redeemed loyalty points balance for a user.
     *
     * @param userId The ID of the user whose redeemed points are retrieved.
     * @return The redeemed loyalty points balance.
     * @throws IllegalStateException if the user record cannot be found.
     */
    fun getRedeemedLoyaltyPointsByUserId(userId: Int): Int =
        transaction {
            val currentRedeemedPoints =
                UserTable
                    .select { UserTable.id eq userId }
                    .map { it[UserTable.redeemedLoyaltyPoints] }
                    .singleOrNull() ?: throw IllegalStateException("User points not found")

            return@transaction currentRedeemedPoints
        }

    /**
     * Adds loyalty points to a user based on the booking amount.
     *
     * @param userId The ID of the user receiving points.
     * @param amount The booking amount used to calculate points.
     * @return The number of points added.
     */
    fun addLoyaltyPointsByUserIdAndBookingAmount(
        userId: Int,
        amount: BigDecimal,
    ): Int =
        transaction {
            val points = amount.toInt()
            val currentPoints = getLoyaltyPointsByUserId(userId)

            UserTable.update({ UserTable.id eq userId }) {
                it[loyaltyPoints] = currentPoints + points
            }

            return@transaction points
        }

    /**
     * Applies all available loyalty points as a discount to the given price.
     *
     * @param userId The ID of the user applying points.
     * @param price The original price before discount.
     * @return The discounted price after loyalty points are applied.
     */
    fun useUsersLoyaltyPoints(
        userId: Int,
        price: BigDecimal,
    ): BigDecimal =
        transaction {
            val loyaltyPoints = getLoyaltyPointsByUserId(userId)
            val redeemedLoyaltyPoints = getRedeemedLoyaltyPointsByUserId(userId)

            val discount = loyaltyPoints.toBigDecimal() / LOYALTY_POINT_DIVISOR
            val discountedPrice = (price - discount).max(BigDecimal.ZERO).setScale(PRICE_SCALE, PRICE_ROUNDING_MODE)

            // Set redeemed to current
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.redeemedLoyaltyPoints] = redeemedLoyaltyPoints + loyaltyPoints
            }
            // set current to no loyalty points
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.loyaltyPoints] = NO_LOYALTY_POINTS
            }

            return@transaction discountedPrice
        }

    /**
     * Adds a passenger record to an existing booking.
     *
     * @param bookingId The ID of the booking for which the passenger is added.
     * @param firstname The passenger's first name.
     * @param lastname The passenger's last name.
     * @param passportCode The passenger's optional passport code.
     * @return The created passenger record.
     */
    fun addPassenger(
        bookingId: Int,
        firstname: String,
        lastname: String,
        passportCode: String?,
    ): Passenger =
        transaction {
            val passengerId =
                PassengerTable.insert {
                    it[PassengerTable.bookingId] = bookingId
                    it[PassengerTable.firstname] = firstname
                    it[PassengerTable.lastname] = lastname
                    it[PassengerTable.passportCode] = passportCode
                } get PassengerTable.id

            Passenger(
                id = passengerId,
                bookingId = bookingId,
                firstname = firstname,
                lastname = lastname,
                passportCode = passportCode,
            )
        }

    /**
     * Assigns a ticket to a passenger for a specific flight seat.
     *
     * @param passengerId The passenger's ID.
     * @param flightSeatId The flight seat ID for the ticket.
     * @param ticketPrice The price of the ticket.
     * @param seatNumber The seat number assigned to the passenger.
     * @return The ticket assignment record.
     */
    fun ticketAssignment(
        passengerId: Int,
        flightSeatId: Int,
        ticketPrice: BigDecimal,
        seatNumber: String,
    ): TicketAssignment =
        transaction {
            val ticketAssignmentId =
                TicketAssignmentTable.insert {
                    it[TicketAssignmentTable.passengerId] = passengerId
                    it[TicketAssignmentTable.flightSeatId] = flightSeatId
                    it[TicketAssignmentTable.ticketPrice] = ticketPrice
                    it[TicketAssignmentTable.seatNumber] = seatNumber
                } get TicketAssignmentTable.id

            TicketAssignment(
                id = ticketAssignmentId,
                passengerId = passengerId,
                flightSeatId = flightSeatId,
                ticketPrice = ticketPrice,
                seatNumber = seatNumber,
            )
        }

    /**
     * Deletes any existing ticket assignments for a booking, releasing seat selections.
     *
     * @param bookingReference The booking reference used to identify the booking.
     */
    fun deleteOldSeatSelectionsByBookingReference(bookingReference: String) =
        transaction {
            val booking = getBookingByReference(bookingReference) ?: return@transaction

            val passengerIds = getPassengersByBookingId(booking.id).map { it.id }
            if (passengerIds.isEmpty()) {
                return@transaction
            }

            TicketAssignmentTable.deleteWhere { SqlExpressionBuilder.run { TicketAssignmentTable.passengerId inList passengerIds } }
        }

    /**
     * Deletes a booking and all related passenger and ticket assignment records.
     *
     * @param bookingReference The booking reference to delete.
     */
    fun deleteBookingByReference(bookingReference: String) =
        transaction {
            val booking = getBookingByReference(bookingReference) ?: return@transaction

            val passengerIds = getPassengersByBookingId(booking.id).map { it.id }

            TicketAssignmentTable.deleteWhere { SqlExpressionBuilder.run { TicketAssignmentTable.passengerId inList passengerIds } }
            PassengerTable.deleteWhere { SqlExpressionBuilder.run { PassengerTable.bookingId eq booking.id } }
            BookingTable.deleteWhere { SqlExpressionBuilder.run { BookingTable.bookingReference eq bookingReference } }
        }

    /**
     * Cancels a booking and, when applicable, processes a refund and loyalty point adjustment.
     *
     * @param bookingReference The booking reference to cancel.
     * @return The cancelled booking or null if the booking does not exist.
     */
    fun cancelBooking(bookingReference: String): Booking? =
        transaction {
            val booking =
                BookingTable.select { BookingTable.bookingReference eq bookingReference }
                    .map { resultRowToBooking(it) }
                    .singleOrNull() ?: return@transaction null

            val userId = booking.userId

            if (booking.status == BookingStatus.CANCELLED) {
                return@transaction booking
            }

            val passengerIds = getPassengersByBookingId(booking.id).map { it.id }

            // 1.Release selected seats
            // do this by deleting ticket assignments only
            // do not delete passengers or booking record
            if (passengerIds.isNotEmpty()) {
                TicketAssignmentTable.deleteWhere {
                    SqlExpressionBuilder.run {
                        TicketAssignmentTable.passengerId inList passengerIds
                    }
                }
            }
            // 2.Keep the booking record,only update status to cancelled
            BookingTable.update({ BookingTable.bookingReference eq bookingReference }) {
                it[status] = BookingStatus.CANCELLED
            }

            if (booking.status == BookingStatus.CREATED) {
                return@transaction booking.copy(status = BookingStatus.CANCELLED)
            }

            // 3.Find original payment amount
            val paidAmount =
                PaymentTable
                    .select { PaymentTable.bookingId eq booking.id }
                    .map { row -> row[PaymentTable.amount] }
                    .singleOrNull() ?: return@transaction null

            // Reduce Loyalty Points gained and refund less if necessary
            val usersLoyaltyPoints = getLoyaltyPointsByUserId(userId)
            val changedPoints = usersLoyaltyPoints - paidAmount.toInt()
            val refundAmount =
                if (changedPoints <= ZERO) {
                    val deficitPoints = -changedPoints

                    val penalty =
                        BigDecimal(deficitPoints)
                            .divide(LOYALTY_POINT_DIVISOR, LOYALTY_POINT_REFUND_SCALE, RoundingMode.HALF_UP)

                    paidAmount - penalty
                } else {
                    UserTable.update({ UserTable.id eq userId }) {
                        it[loyaltyPoints] = changedPoints
                    }
                    paidAmount
                }

            // 4.Mark payment as refunded
            PaymentTable.update({ PaymentTable.bookingId eq booking.id }) {
                it[paymentStatus] = PaymentStatus.REFUNDED
                it[PaymentTable.refundAmount] = refundAmount
                it[refundDate] = LocalDateTime.now()
            }
            booking.copy(status = BookingStatus.CANCELLED)
        }

    // change flight info(user-side request)

    /**
     * Creates a customer flight information change request for a booking.
     *
     * @param userId The user submitting the request.
     * @param bookingReference The booking reference for the request.
     * @param requestType The type of flight information request.
     * @param passengerId The optional passenger ID affected by the request.
     * @param newFirstname The optional new first name.
     * @param newLastname The optional new last name.
     * @param newPassportCode The optional new passport code.
     * @param requestedFlightCode The optional requested flight code.
     * @param message The optional message describing the request.
     * @return True if the request was created successfully, false otherwise.
     */
    fun createFlightInfoRequest(
        userId: Int,
        bookingReference: String,
        requestType: FlightInfoRequestType,
        passengerId: Int?,
        newFirstname: String?,
        newLastname: String?,
        newPassportCode: String?,
        requestedDepartureDate: LocalDate?,
        message: String?,
    ): Boolean =
        transaction {
            val booking =
                BookingTable
                    .select { BookingTable.bookingReference eq bookingReference }
                    .singleOrNull() ?: return@transaction false
            val bookingId = booking[BookingTable.id]

            if (booking[BookingTable.userId] != userId) {
                return@transaction false
            }

            FlightInfoRequestTable.insert {
                it[FlightInfoRequestTable.userId] = userId
                it[FlightInfoRequestTable.bookingId] = booking[BookingTable.id]
                it[FlightInfoRequestTable.requestType] = requestType
                it[FlightInfoRequestTable.status] = FlightInfoRequestStatus.PENDING
                it[FlightInfoRequestTable.passengerId] = passengerId
                it[FlightInfoRequestTable.newFirstname] = newFirstname
                it[FlightInfoRequestTable.newLastname] = newLastname
                it[FlightInfoRequestTable.newPassportCode] = newPassportCode
                it[FlightInfoRequestTable.requestedDepartureDate] = requestedDepartureDate
                it[FlightInfoRequestTable.message] = message
                it[FlightInfoRequestTable.adminReply] = null
                it[FlightInfoRequestTable.createdAt] = LocalDateTime.now()
                it[FlightInfoRequestTable.handledAt] = null
                it[FlightInfoRequestTable.handledByUserId] = null
            }
            true
        }

    /**
     * Retrieves flight information change requests submitted by a specific user.
     *
     * @param userId The ID of the user whose requests should be retrieved.
     * @return A list of flight information request summaries.
     */
    fun getFlightInfoRequestsByUserId(userId: Int): List<FlightInfoRequestSummary> =
        transaction {
            FlightInfoRequestTable
                .join(
                    BookingTable,
                    JoinType.INNER,
                    FlightInfoRequestTable.bookingId,
                    BookingTable.id,
                )
                .join(
                    UserTable,
                    JoinType.INNER,
                    FlightInfoRequestTable.userId,
                    UserTable.id,
                )
                .join(
                    FlightTable,
                    JoinType.INNER,
                    BookingTable.flightId,
                    FlightTable.id,
                )
                .select { FlightInfoRequestTable.userId eq userId }
                .orderBy(FlightInfoRequestTable.createdAt, SortOrder.DESC)
                .map { row ->
                    FlightInfoRequestSummary(
                        id = row[FlightInfoRequestTable.id],
                        bookingReference = row[BookingTable.bookingReference],
                        userId = row[FlightInfoRequestTable.userId],
                        customerName =
                            listOfNotNull(
                                row[UserTable.firstname],
                                row[UserTable.lastname],
                            ).joinToString(" "),
                        email = row[UserTable.email],
                        currentFlightCode = row[FlightTable.flightCode],
                        currentDepartureTime = row[FlightTable.departureTime],
                        currentArrivalTime = row[FlightTable.arrivalTime],
                        requestedDepartureDate = row[FlightInfoRequestTable.requestedDepartureDate],
                        requestType = row[FlightInfoRequestTable.requestType],
                        status = row[FlightInfoRequestTable.status],
                        passengerId = row[FlightInfoRequestTable.passengerId],
                        newFirstname = row[FlightInfoRequestTable.newFirstname],
                        newLastname = row[FlightInfoRequestTable.newLastname],
                        newPassportCode = row[FlightInfoRequestTable.newPassportCode],
                        message = row[FlightInfoRequestTable.message],
                        adminReply = row[FlightInfoRequestTable.adminReply],
                        createdAt = row[FlightInfoRequestTable.createdAt],
                        handledAt = row[FlightInfoRequestTable.handledAt],
                    )
                }
        }

    /**
     * Confirms a booking by updating its status to CONFIRMED.
     *
     * @param booking The booking to confirm.
     * @return The number of rows updated.
     */
    fun confirmBooking(booking: Booking): Int =
        transaction {
            BookingTable.update({ BookingTable.id eq booking.id }) {
                it[status] = BookingStatus.CONFIRMED
            }
        }

    /**
     * Calculates the ticket price based on flight base price, seat class, and travel date.
     *
     * @param flightPrice The base flight price.
     * @param seatClass The chosen seat class or null for economy.
     * @param date The travel date or null if unspecified.
     * @return The final calculated ticket price.
     */
    fun calculatePrice(
        flightPrice: BigDecimal,
        seatClass: SeatClass?,
        date: LocalDate?,
    ): BigDecimal =
        transaction {
            val now = LocalDate.now()
            val seatClassMultiplier =
                when (seatClass) {
                    SeatClass.ECONOMY -> ECONOMY_PRICE_MULTIPLIER
                    SeatClass.BUSINESS -> BUSINESS_PRICE_MULTIPLIER
                    SeatClass.FIRST -> FIRST_CLASS_PRICE_MULTIPLIER
                    null -> DEFAULT_PRICE_MULTIPLIER
                }
            val dateMultiplier =
                when {
                    date == null -> DEFAULT_PRICE_MULTIPLIER
                    date == now -> SAME_DAY_PRICE_MULTIPLIER
                    date <= now.plusDays(SHORT_TERM_BOOKING_DAYS) -> THREE_DAY_PRICE_MULTIPLIER
                    date <= now.plusWeeks(ONE_WEEK_BOOKING_WEEKS) -> ONE_WEEK_PRICE_MULTIPLIER
                    date <= now.plusWeeks(TWO_WEEK_BOOKING_WEEKS) -> TWO_WEEK_PRICE_MULTIPLIER
                    date <= now.plusWeeks(FOUR_WEEK_BOOKING_WEEKS) -> FOUR_WEEK_PRICE_MULTIPLIER
                    else -> DEFAULT_PRICE_MULTIPLIER
                }
            val ticketPrice =
                flightPrice
                    .multiply(seatClassMultiplier)
                    .multiply(dateMultiplier)
                    .setScale(PRICE_SCALE, PRICE_ROUNDING_MODE)

            return@transaction ticketPrice
        }

    /**
     * Retrieves seat availability for a specific flight.
     *
     * @param flightId The ID of the flight to check.
     * @return A list of seat availability details for the flight.
     */
    fun getSeatsByFlightId(flightId: Int): List<SeatAvailability> =
        transaction {
            FlightSeatTable
                .join(SeatTable, JoinType.INNER, FlightSeatTable.seatId, SeatTable.id)
                .join(TicketAssignmentTable, JoinType.LEFT, FlightSeatTable.id, TicketAssignmentTable.flightSeatId)
                .select { FlightSeatTable.flightId eq flightId }
                .map {
                    val available = (it.getOrNull(TicketAssignmentTable.id) == null)

                    SeatAvailability(
                        flightSeatId = it[FlightSeatTable.id],
                        seatNumber = it[SeatTable.seatNumber],
                        seatClass = it[SeatTable.seatClass],
                        available = available,
                    )
                }
        }

    /**
     * Retrieves seat selections for a flight and specific passengers.
     *
     * @param flightId The flight ID used to find seats.
     * @param passengers The list of passengers whose seat assignments are retrieved.
     * @return A list of selected seats for the passengers.
     */
    fun getSelectedSeatsByFlightIdAndPassengers(
        flightId: Int,
        passengers: List<Passenger>,
    ): List<SelectedSeat> =
        transaction {
            val passengerIds = passengers.map { it.id }

            FlightSeatTable
                .join(SeatTable, JoinType.INNER, FlightSeatTable.seatId, SeatTable.id)
                .join(TicketAssignmentTable, JoinType.INNER, FlightSeatTable.id, TicketAssignmentTable.flightSeatId)
                .select {
                    (FlightSeatTable.flightId eq flightId) and
                        (TicketAssignmentTable.passengerId inList passengerIds)
                }
                .map {
                    val passengerId = it[TicketAssignmentTable.passengerId]
                    val passenger = passengers.single { p -> p.id == passengerId } // p instaed of inner it

                    SelectedSeat(
                        flightSeatId = it[FlightSeatTable.id],
                        seatNumber = it[SeatTable.seatNumber],
                        seatClass = it[SeatTable.seatClass],
                        passenger = passenger,
                    )
                }
        }

    /**
     * Retrieves all passengers associated with a booking.
     *
     * @param bookingId The ID of the booking.
     * @return A list of passengers for the booking.
     */
    fun getPassengersByBookingId(bookingId: Int): List<Passenger> =
        transaction {
            PassengerTable
                .select { PassengerTable.bookingId eq bookingId }
                .map { resultRowToPassenger(it) }
        }

    /**
     * Retrieves all bookings for a specific user.
     *
     * @param userId The user whose bookings are retrieved.
     * @return A list of bookings for the user.
     */
    fun getBookingsByUserId(userId: Int): List<Booking> =
        transaction {
            BookingTable
                .select { BookingTable.userId eq userId }
                .map { resultRowToBooking(it) }
        }

    /**
     * Retrieves a booking by its reference code.
     *
     * @param bookingReference The booking reference string.
     * @return The matching booking, or null if not found.
     */
    fun getBookingByReference(bookingReference: String): Booking? =
        transaction {
            BookingTable
                .select { BookingTable.bookingReference eq bookingReference }
                .map { resultRowToBooking(it) }
                .singleOrNull()
        }

    /**
     * Calculates the total ticket price for a booking.
     *
     * @param bookingId The ID of the booking.
     * @return The total ticket amount for the booking.
     */
    fun getBookingPricePriceByBookingId(bookingId: Int): BigDecimal =
        transaction {
            val ticketPrice =
                TicketAssignmentTable
                    .join(PassengerTable, JoinType.INNER, TicketAssignmentTable.passengerId, PassengerTable.id)
                    .select { PassengerTable.bookingId eq bookingId }
                    .map { it[TicketAssignmentTable.ticketPrice] }
                    .fold(BigDecimal.ZERO) { acc, price -> acc + price }
            return@transaction ticketPrice
        }

    fun getTicketInfoByPassengerAndBooking(
        passenger: Passenger,
        booking: Booking,
    ): TicketInfo =
        transaction {
            val passengerName = passenger.firstname + " " + passenger.lastname
            val bookingReference = booking.bookingReference
            val flightId = booking.flightId

            val flight =
                FlightTable
                    .select { FlightTable.id eq flightId }
                    .singleOrNull() ?: throw IllegalStateException("Flight not found")

            val seatNumber =
                TicketAssignmentTable
                    .join(FlightSeatTable, JoinType.INNER, TicketAssignmentTable.flightSeatId, FlightSeatTable.id)
                    .select {
                        (TicketAssignmentTable.passengerId eq passenger.id) and
                            (FlightSeatTable.flightId eq booking.flightId)
                    }
                    .map { it[TicketAssignmentTable.seatNumber] }
                    .singleOrNull() ?: throw IllegalStateException("Outbound seat number not found")

            val flightSeatId =
                TicketAssignmentTable
                    .join(FlightSeatTable, JoinType.INNER, TicketAssignmentTable.flightSeatId, FlightSeatTable.id)
                    .select {
                        (TicketAssignmentTable.passengerId eq passenger.id) and
                            (FlightSeatTable.flightId eq booking.flightId)
                    }
                    .map { it[TicketAssignmentTable.flightSeatId] }
                    .singleOrNull() ?: throw IllegalStateException("Outbound flight seat id not found")

            val seatClass = getSeatClassByFlightSeatId(flightSeatId) ?: throw IllegalStateException("Outbound seat class not found")

            val departureAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq flight[FlightTable.departureAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Departure Airport not found")
            val arrivalAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq flight[FlightTable.arrivalAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Arrival Airport not found")

            val dateTime = flight[FlightTable.departureTime]

            TicketInfo(
                passengerName = passengerName,
                bookingReference = bookingReference,
                seatNumber = seatNumber,
                seatClass = seatClass,
                departureAirportNameCode = departureAirportNameCode,
                arrivalAirportNameCode = arrivalAirportNameCode,
                dateTime = dateTime,
            )
        }

    fun getReturnTicketInfoByPassengerAndBooking(
        passenger: Passenger,
        booking: Booking,
    ): TicketInfo =
        transaction {
            val passengerName = passenger.firstname + " " + passenger.lastname
            val bookingReference = booking.bookingReference
            val returnFlightId = booking.returnFlightId ?: throw IllegalStateException("No return flight on this booking")

            val returnFlight =
                FlightTable
                    .select { FlightTable.id eq returnFlightId }
                    .singleOrNull() ?: throw IllegalStateException("Flight not found")

            val returnSeatNumber =
                TicketAssignmentTable
                    .join(FlightSeatTable, JoinType.INNER, TicketAssignmentTable.flightSeatId, FlightSeatTable.id)
                    .select {
                        (TicketAssignmentTable.passengerId eq passenger.id) and
                            (FlightSeatTable.flightId eq booking.returnFlightId)
                    }
                    .map { it[TicketAssignmentTable.seatNumber] }
                    .singleOrNull() ?: throw IllegalStateException("Return seat not found")

            val returnFlightSeatId =
                TicketAssignmentTable
                    .join(FlightSeatTable, JoinType.INNER, TicketAssignmentTable.flightSeatId, FlightSeatTable.id)
                    .select {
                        (TicketAssignmentTable.passengerId eq passenger.id) and
                            (FlightSeatTable.flightId eq booking.returnFlightId)
                    }
                    .map { it[TicketAssignmentTable.flightSeatId] }
                    .singleOrNull() ?: throw IllegalStateException("Return flight seat id not found")

            val returnSeatClass =
                getSeatClassByFlightSeatId(returnFlightSeatId)
                    ?: throw IllegalStateException("Return seat class not found")

            val departureAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq returnFlight[FlightTable.departureAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Departure Airport not found")
            val arrivalAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq returnFlight[FlightTable.arrivalAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Arrival Airport not found")

            val dateTime = returnFlight[FlightTable.departureTime]

            TicketInfo(
                passengerName = passengerName,
                bookingReference = bookingReference,
                seatNumber = returnSeatNumber,
                seatClass = returnSeatClass,
                departureAirportNameCode = departureAirportNameCode,
                arrivalAirportNameCode = arrivalAirportNameCode,
                dateTime = dateTime,
            )
        }

    /**
     * Builds detailed booking information including flight, passenger, and payment data.
     *
     * @param booking The booking used to construct the information.
     * @return The booking information summary.
     * @throws IllegalStateException if required flight or airport details cannot be found.
     */
    fun getBookingInfoByBooking(booking: Booking): BookingInfo =
        transaction {
            val flight =
                FlightTable
                    .select { FlightTable.id eq booking.flightId }
                    .singleOrNull() ?: throw IllegalStateException("Flight not found")

            val departureAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq flight[FlightTable.departureAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Departure Airport not found")
            val arrivalAirportNameCode =
                AirportTable
                    .select { AirportTable.id eq flight[FlightTable.arrivalAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Arrival Airport not found")

            val dateTime = flight[FlightTable.departureTime]

            val numOfPassengers =
                PassengerTable
                    .select { PassengerTable.bookingId eq booking.id }
                    .count()
            val amountPaid =
                PaymentTable
                    .select { PaymentTable.bookingId eq booking.id }
                    .map { it[PaymentTable.amount] }
                    .singleOrNull()

            var returnFlightCode: String? = null
            var returnDepartureAirportNameCode: String? = null
            var returnArrivalAirportNameCode: String? = null
            var returnDateTime: LocalDateTime? = null

            val returnFlightId = booking.returnFlightId
            if (returnFlightId != null) {
                val returnFlight =
                    FlightTable
                        .select { FlightTable.id eq returnFlightId }
                        .singleOrNull() ?: throw IllegalStateException("Return Flight not found")

                returnFlightCode = returnFlight[FlightTable.flightCode]
                returnDepartureAirportNameCode = AirportTable
                    .select { AirportTable.id eq returnFlight[FlightTable.departureAirportId] }
                    .map {
                        it[AirportTable.name] + " " + it[AirportTable.code]
                    }
                    .singleOrNull() ?: throw IllegalStateException("Return Departure Airport not found")
                returnArrivalAirportNameCode = AirportTable
                    .select { AirportTable.id eq returnFlight[FlightTable.arrivalAirportId] }
                    .map {
                        it[AirportTable.name] + " " + it[AirportTable.code]
                    }
                    .singleOrNull() ?: throw IllegalStateException("Return Arrival Airport not found")
                returnDateTime = returnFlight[FlightTable.departureTime]
            }

            BookingInfo(
                bookingReference = booking.bookingReference,
                flightCode = flight[FlightTable.flightCode],
                returnFlightCode = returnFlightCode,
                bookingStatus = booking.status,
                numOfPassengers = numOfPassengers,
                departureAirportNameCode = departureAirportNameCode,
                arrivalAirportNameCode = arrivalAirportNameCode,
                returnDepartureAirportNameCode = returnDepartureAirportNameCode,
                returnArrivalAirportNameCode = returnArrivalAirportNameCode,
                departureTime = dateTime,
                returnDepartureTime = returnDateTime,
                // management ui combine modify
                flightStatus = flight[FlightTable.status],
                amountPaid = amountPaid,
            )
        }

    /**
     * Retrieves the seat class for a specific flight seat.
     *
     * @param flightSeatId The flight seat ID.
     * @return The seat class or null if no seat is found.
     */
    fun getSeatClassByFlightSeatId(flightSeatId: Int): SeatClass? =
        transaction {
            FlightSeatTable
                .join(SeatTable, JoinType.INNER, FlightSeatTable.seatId, SeatTable.id)
                .select { FlightSeatTable.id eq flightSeatId }
                .map { it[SeatTable.seatClass] }
                .singleOrNull()
        }

    /**
     * Retrieves a seat number by flight seat ID.
     *
     * @param flightSeatId The flight seat ID.
     * @return The seat number, or null if the seat is not found.
     */
    fun getSeatNumberByFlightSeatId(flightSeatId: Int): String? =
        transaction {
            FlightSeatTable
                .join(SeatTable, JoinType.INNER, FlightSeatTable.seatId, SeatTable.id)
                .select { FlightSeatTable.id eq flightSeatId }
                .map { it[SeatTable.seatNumber] }
                .singleOrNull()
        }

    /**
     * Maps a database row to a Booking model.
     *
     * @param row The result row containing booking column values.
     * @return The booking model.
     */
    fun resultRowToBooking(row: ResultRow): Booking {
        return Booking(
            id = row[BookingTable.id],
            bookingReference = row[BookingTable.bookingReference],
            userId = row[BookingTable.userId],
            flightId = row[BookingTable.flightId],
            returnFlightId = row[BookingTable.returnFlightId],
            status = row[BookingTable.status],
            createdAt = row[BookingTable.createdAt],
        )
    }

    /**
     * Maps a database row to a Passenger model.
     *
     * @param row The result row containing passenger column values.
     * @return The passenger model.
     */
    fun resultRowToPassenger(row: ResultRow): Passenger {
        return Passenger(
            id = row[PassengerTable.id],
            bookingId = row[PassengerTable.bookingId],
            firstname = row[PassengerTable.firstname],
            lastname = row[PassengerTable.lastname],
            passportCode = row[PassengerTable.passportCode],
        )
    }

    /**
     * Maps a database row to a Seat model.
     *
     * @param row The result row containing seat column values.
     * @return The seat model.
     */
    fun resultRowToSeat(row: ResultRow): Seat {
        return Seat(
            id = row[SeatTable.id],
            aircraftId = row[SeatTable.aircraftId],
            seatNumber = row[SeatTable.seatNumber],
            seatClass = row[SeatTable.seatClass],
        )
    }

    /**
     * Generates a new booking reference code.
     *
     * @return A random uppercase booking reference string.
     */
    fun generateBookingReference(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, BOOKING_REFERENCE_LENGTH).uppercase()
    }
}
