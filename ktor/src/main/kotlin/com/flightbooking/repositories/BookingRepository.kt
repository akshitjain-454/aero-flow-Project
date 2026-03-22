package com.flightbooking.repositories

import com.flightbooking.models.Booking
import com.flightbooking.models.Seat
import com.flightbooking.models.Payment
import com.flightbooking.models.SeatAvailability
import com.flightbooking.models.TicketInfo
import com.flightbooking.models.TicketAssignment
import com.flightbooking.models.Passenger
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.UserTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID
import java.math.BigDecimal

class BookingRepository {

    fun createBooking(userId: Int, flightId: Int): Booking = transaction { 
        val now = LocalDateTime.now()
        val reference = generateBookingReference()

        val bookingId = BookingTable.insert{
            it[bookingReference] = reference
            it[BookingTable.userId] = userId
            it[BookingTable.flightId]= flightId
            it[status] = BookingStatus.CREATED
            it[createdAt] = now
        } get BookingTable.id

        Booking (
            id = bookingId,
            bookingReference = reference,
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CREATED,
            createdAt = now
        )
    }

    fun createPayment(bookingId: Int, amount: BigDecimal, paymentMethod: PaymentMethod): Payment = transaction { 
        val now = LocalDateTime.now()
        val transactionId = UUID.randomUUID().toString().replace("-", "").substring(0, 10).uppercase()

        val paymentId = PaymentTable.insert{
            it[PaymentTable.bookingId] = bookingId
            it[PaymentTable.amount] = amount
            it[PaymentTable.paymentStatus]= PaymentStatus.COMPLETED
            it[PaymentTable.paymentMethod] = paymentMethod
            it[PaymentTable.transactionId] = transactionId
            it[createdAt] = now
        } get PaymentTable.id

        Payment (
            id = paymentId,
            bookingId = bookingId,
            amount = amount,
            paymentStatus = PaymentStatus.COMPLETED,
            paymentMethod = paymentMethod,
            transactionId = transactionId,
            createdAt = now,
            refundAmount = null,
            refundDate = null
        )
    }

    fun addPassenger(bookingId: Int, firstname: String, lastname: String, passportCode: String?): Passenger = transaction {
        val passengerId = PassengerTable.insert {
            it[PassengerTable.bookingId] = bookingId
            it[PassengerTable.firstname] = firstname
            it[PassengerTable.lastname]= lastname
            it[PassengerTable.passportCode] = passportCode
        } get PassengerTable.id

        Passenger (
            id = passengerId,
            bookingId = bookingId,
            firstname = firstname,
            lastname = lastname,
            passportCode = passportCode
        ) 
    }

    fun ticketAssignment(passengerId: Int, flightSeatId: Int, ticketPrice: BigDecimal, seatNumber: String): TicketAssignment = transaction {
        val ticketAssignmentId = TicketAssignmentTable.insert {
            it[TicketAssignmentTable.passengerId] = passengerId
            it[TicketAssignmentTable.flightSeatId] = flightSeatId
            it[TicketAssignmentTable.ticketPrice] = ticketPrice
            it[TicketAssignmentTable.seatNumber] = seatNumber
        } get TicketAssignmentTable.id

        TicketAssignment (
            id = ticketAssignmentId,
            passengerId = passengerId,
            flightSeatId = flightSeatId,
            ticketPrice = ticketPrice,
            seatNumber = seatNumber
        ) 
    }

    fun cancelBooking(bookingReference: String): Booking? = transaction {
        val booking = BookingTable.select { BookingTable.bookingReference eq bookingReference }
            .map { resultRowToBooking(it) }
            .singleOrNull() ?: return@transaction null

        if (booking.status == BookingStatus.CANCELLED) {
            return@transaction booking
        }

        BookingTable.update({ BookingTable.bookingReference eq bookingReference }) {
            it[status] = BookingStatus.CANCELLED
        }

        booking.copy(status = BookingStatus.CANCELLED)
    }

    fun calculatePrice(flightPrice: BigDecimal, seatClass: SeatClass?, date: LocalDate?): BigDecimal = transaction {
        val now = LocalDate.now()
        val seatClassMultiplier = when (seatClass) { 
            SeatClass.ECONOMY -> BigDecimal("1.0")
            SeatClass.BUSINESS -> BigDecimal("1.75")
            SeatClass.FIRST -> BigDecimal("3.0")
            null -> BigDecimal("1.0")
        }
        val dateMultiplier = when {
            date == null -> BigDecimal("1.0")
            date == now -> BigDecimal("1.75")
            date <= now.plusDays(3) -> BigDecimal("1.5")
            date <= now.plusWeeks(1) -> BigDecimal("1.25")
            date <= now.plusWeeks(2) -> BigDecimal("1.15")
            date <= now.plusWeeks(4) -> BigDecimal("1.1")
            else -> BigDecimal("1.0")
        }
        val ticketPrice = flightPrice.multiply(seatClassMultiplier).multiply(dateMultiplier)

        return@transaction ticketPrice
    }

    fun getSeatsByFlightId(flightId: Int): List<SeatAvailability> = transaction { 
        FlightSeatTable
            .join(SeatTable, JoinType.INNER, FlightSeatTable.seatId, SeatTable.id )
            .join(TicketAssignmentTable, JoinType.LEFT, FlightSeatTable.id, TicketAssignmentTable.flightSeatId)
            .select { FlightSeatTable.flightId eq flightId }
            .map { 
                val available = (it.getOrNull(TicketAssignmentTable.id) == null)

                SeatAvailability(
                    flightSeatId = it[FlightSeatTable.id],
                    seatNumber = it[SeatTable.seatNumber],
                    seatClass = it[SeatTable.seatClass],
                    available = available
                )
            }
    }

    fun getPassengersByBookingId(bookingId: Int): List<Passenger> = transaction {
        PassengerTable
            .select { PassengerTable.bookingId eq bookingId }
            .map { resultRowToPassenger(it) }
    }

    fun getBookingsByUserId(userId: Int): List<Booking> = transaction {
        BookingTable
            .select { BookingTable.userId eq userId }
            .map { resultRowToBooking(it) }
    }

    fun getBookingByReference(bookingReference: String): Booking? = transaction {
        BookingTable
            .select { BookingTable.bookingReference eq bookingReference }
            .map { resultRowToBooking(it) }.singleOrNull()
    }

    fun getTicketPriceByPassengerId(passengerId: Int): BigDecimal? = transaction {
        val ticketPrice = TicketAssignmentTable
            .select { TicketAssignmentTable.passengerId eq passengerId }
            .map { it[TicketAssignmentTable.ticketPrice] }.singleOrNull()
        return@transaction ticketPrice
    }

    fun getTicketInfoByPassengerAndBooking(passenger: Passenger, booking: Booking): TicketInfo = transaction {
        val passengerName = passenger.firstname + " " + passenger.lastname
        val bookingReference = booking.bookingReference
        val flightId = booking.flightId

        val flight = FlightTable
            .select { FlightTable.id eq flightId }
            .singleOrNull() ?: throw IllegalStateException("Flight not found")
            
        val seatNumber = TicketAssignmentTable
            .select { TicketAssignmentTable.passengerId eq passenger.id }
            .map { it[TicketAssignmentTable.seatNumber] }.singleOrNull() ?: throw IllegalStateException("Seat not found")
        
        val departureAirport = AirportTable
            .select { AirportTable.id eq flight[FlightTable.departureAirportId] }
            .map { it[AirportTable.name] + " " + it[AirportTable.code] }.singleOrNull() ?: throw IllegalStateException("Departure Airport not found")
            val arrivalAirport = AirportTable
            .select { AirportTable.id eq flight[FlightTable.arrivalAirportId] }
            .map { it[AirportTable.name] + " " + it[AirportTable.code] }.singleOrNull() ?: throw IllegalStateException("Arrival Airport not found")


        val dateTime = flight[FlightTable.departureTime]
        
        TicketInfo(
            passengerName = passengerName,
            bookingReference = bookingReference,
            seatNumber = seatNumber,
            departureAirport = departureAirport,
            arrivalAirport = arrivalAirport,
            dateTime = dateTime,
        )
    }

    fun resultRowToBooking(row: ResultRow): Booking {
        return Booking (
            id = row[BookingTable.id],
            bookingReference = row[BookingTable.bookingReference],
            userId = row[BookingTable.userId],
            flightId = row[BookingTable.flightId],
            status = row[BookingTable.status],
            createdAt = row[BookingTable.createdAt],
        )
    }

    fun resultRowToPassenger(row: ResultRow): Passenger {
        return Passenger (
            id = row[PassengerTable.id],
            bookingId = row[PassengerTable.bookingId],
            firstname = row[PassengerTable.firstname],
            lastname = row[PassengerTable.lastname],
            passportCode = row[PassengerTable.passportCode]
        )
    }

    fun resultRowToSeat(row: ResultRow): Seat {
        return Seat (
            id = row[SeatTable.id],
            aircraftId = row[SeatTable.aircraftId],
            seatNumber = row[SeatTable.seatNumber],
            seatClass = row[SeatTable.seatClass]
        )
    }


    fun generateBookingReference(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()
    }

}