package com.flightbooking.repositories

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.models.BookingsPerFlightReport
import com.flightbooking.models.CancelledBookingSummary
import com.flightbooking.models.FlightAvailabilitySummary
import com.flightbooking.models.FlightChangeLogInfo
import com.flightbooking.models.Flight
import com.flightbooking.models.CancelledFlightSummary
import com.flightbooking.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.LocalDate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import com.flightbooking.models.MostPopularRouteReport
import com.flightbooking.models.PeakBookingTimeReport
import com.flightbooking.models.ReservationSummary
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.models.FlightInfoRequestSummary
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.TextColumnType
import com.flightbooking.tables.PaymentTable

/**
 * This Adminrepository is responsible for generating management reports, retrieving reservation and flight information, updating flight schedules
 * and statuses, and handling customer flight information change requests.
 * 
 * Provides data access and business logic for administrator functions.
 * Database operations are performed using Exposed transactions.
 */

class AdminRepository {

    private val flightRepository = FlightRepository()

    /**
     * Generates a report showing the number of confirmed bookings for each flight.
     *
     * The result includes flight details, airport information, aircraft type, flight status, and the total number of bookings for each flight.
     * Cancelled bookings are excluded from the booking count. 
     *
     * Return a list of booking count summaries grouped by flight.
     */
    fun getBookingsPerFlightReport(): List<BookingsPerFlightReport> = transaction {
        val bookingCountExpr = BookingTable.id.count()

        BookingTable
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .slice(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
                AircraftTable.type,
                bookingCountExpr
            )
            .select { BookingTable.status neq BookingStatus.CANCELLED }
            .groupBy(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
                AircraftTable.type
            )
            .orderBy(bookingCountExpr, SortOrder.DESC)
            .map { row ->
                BookingsPerFlightReport(
                    flightId = row[FlightTable.id],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportId = row[FlightTable.departureAirportId],
                    arrivalAirportId = row[FlightTable.arrivalAirportId],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    flightStatus = row[FlightTable.status],
                    aircraftType = row[AircraftTable.type],
                    bookingCount = row[bookingCountExpr]
                )
            }
    }

    /**
     * Generates a report of the most popular routes based on the booking count.
     *
     * The most popular Routes are grouped by departure and arrival airports. Cancelled bookings are excluded.
     *
     * Return a list of routes ordered by booking count in descending order.
     */
    fun getMostPopularRoutesReport(): List<MostPopularRouteReport> = transaction {

        val bookingCountExpr = BookingTable.id.count()

        BookingTable
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .slice(
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                bookingCountExpr
            )
            .select { BookingTable.status neq BookingStatus.CANCELLED }
            .groupBy(
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId
            )
            .orderBy(bookingCountExpr, SortOrder.DESC)
            .map { row ->
                MostPopularRouteReport(
                    departureAirportId = row[FlightTable.departureAirportId],
                    arrivalAirportId = row[FlightTable.arrivalAirportId],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    bookingCount = row[bookingCountExpr]
                )
            }
    }

    /**
     * Generates a report showing the peak booking times.
     *
     * Bookings are grouped by the hour in which they were created. Cancelled bookings are excluded.
     *
     * Return a list of booking time slots ordered by booking count.
     */
    fun getPeakBookingTimesReport(): List<PeakBookingTimeReport> = transaction {

        val bookingCountExpr = BookingTable.id.count()
        //This is the SQLite time formatting function strftime change, for example:2026-03-25 14:37:22 in 14:00
        val bookingHourExpr = CustomFunction<String>(
            "strftime",
            TextColumnType(),
            stringLiteral("%H:00"),
            BookingTable.createdAt
        )

        BookingTable
            .slice(
                bookingHourExpr,
                bookingCountExpr
            )
            .select { BookingTable.status neq BookingStatus.CANCELLED }
            .groupBy(bookingHourExpr)
            .orderBy(bookingCountExpr, SortOrder.DESC)
            .map { row ->
                PeakBookingTimeReport(
                    bookingHour = row[bookingHourExpr],
                    bookingCount = row[bookingCountExpr]
                )
            }
    }

    /**
     * Retrieves the booking count report for a single flight code.
     *
     * The flight code is normalised to uppercase before lookup.Cancelled bookings are excluded.
     *
     * Param flightCode: The flight code used to search for the flight.
     * Return a booking summary for the matching flight, or null if no flight exists.
     */
    fun getBookingsPerFlightByFlightCode(flightCode: String): BookingsPerFlightReport? {
        val flight = flightRepository.getFlightByFlightCode(flightCode.uppercase()) ?: return null

        val bookingCount = transaction {
            BookingTable
                .select {
                    (BookingTable.flightId eq flight.id) and
                    (BookingTable.status neq BookingStatus.CANCELLED)
                }
                .count()
        }

        return BookingsPerFlightReport(
            flightId = flight.id,
            flightCode = flight.flightCode,
            departureAirportId = flight.departureAirportId,
            arrivalAirportId = flight.arrivalAirportId,
            departureAirportNameCode = getAirportNameCodeById(flight.departureAirportId),
            arrivalAirportNameCode = getAirportNameCodeById(flight.arrivalAirportId),
            departureTime = flight.departureTime,
            arrivalTime = flight.arrivalTime,
            flightStatus = flight.status,
            bookingCount = bookingCount,
            aircraftType = getAircraftTypeById(flight.aircraftId)
        )
    }

    /**
     * Generates a flight availability report with optional filtering.
     *
     * The report compares total seats against booked seats for each matching flight and calculates the number of available seats.
     *
     * Param fromCodes: Optional list of departure airport codes to filter by.
     * Param toCodes: Optional list of arrival airport codes to filter by.
     * Param date: Optional departure date to filter by.
     * Return a list of flight availability summaries matching the filters.
     */
    fun getFlightAvailabilityReport(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<FlightAvailabilitySummary> = transaction {
        val totalSeatsExpr = FlightSeatTable.id.count()
        val bookedSeatsExpr = TicketAssignmentTable.id.count()
        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable.select { AirportTable.code inList fromCodes }.map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }

        if (toCodes != null) {
            val toIds = AirportTable.select { AirportTable.code inList toCodes }.map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }

        if (date != null) {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.atTime(23, 59, 59)

            selectCondition = selectCondition and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
        }

        (FlightTable innerJoin FlightSeatTable)
            .join(
                TicketAssignmentTable,
                JoinType.LEFT,
                FlightSeatTable.id,
                TicketAssignmentTable.flightSeatId
                )
                .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .slice(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
                AircraftTable.type,
                totalSeatsExpr,
                bookedSeatsExpr
            )
            .select { selectCondition }
            .groupBy(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
                AircraftTable.type
            )
            .orderBy(FlightTable.departureTime, SortOrder.ASC)
            .map { row ->
                val totalSeats = row[totalSeatsExpr]
                val bookedSeats = row[bookedSeatsExpr]

                FlightAvailabilitySummary(
                    flightId = row[FlightTable.id],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportId = row[FlightTable.departureAirportId],
                    arrivalAirportId = row[FlightTable.arrivalAirportId],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    flightStatus = row[FlightTable.status],
                    aircraftType = row[AircraftTable.type],
                    totalSeats = totalSeats,
                    bookedSeats = bookedSeats,
                    availableSeats = totalSeats - bookedSeats
                )
            }
    }

    /**
     * Retrieves cancelled bookings using optional route and date filters.
     *
     * The result includes customer details, booking reference, flight information,
     * aircraft type, and cancellation status.
     *
     * Param fromCodes: Optional list of departure airport codes to filter by.
     * Param toCodes: Optional list of arrival airport codes to filter by.
     * Param date: Optional departure date to filter by.
     * Return a list of cancelled booking summaries.
     */
    fun getCancelledBookings(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<CancelledBookingSummary> = transaction {
        var selectCondition: Op<Boolean> = (BookingTable.status eq BookingStatus.CANCELLED)

        if (fromCodes != null) {
            val fromIds = AirportTable.select { AirportTable.code inList fromCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable.select { AirportTable.code inList toCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }
        if (date != null) {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.atTime(23, 59, 59)
            selectCondition = selectCondition and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
        }
        BookingTable
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .join(
                UserTable,
                JoinType.INNER,
                BookingTable.userId,
                UserTable.id
            )
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .select { selectCondition }
            .orderBy(BookingTable.createdAt, SortOrder.DESC)
            .map { row ->
                val departureAirportNameCode = AirportTable
                    .select { AirportTable.id eq row[FlightTable.departureAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Departure Airport not found")
                val arrivalAirportNameCode = AirportTable
                    .select { AirportTable.id eq row[FlightTable.arrivalAirportId] }
                    .map { it[AirportTable.name] + " " + it[AirportTable.code] }
                    .singleOrNull() ?: throw IllegalStateException("Arrival Airport not found")

                CancelledBookingSummary(
                    bookingId = row[BookingTable.id],
                    bookingReference = row[BookingTable.bookingReference],
                    userId = row[BookingTable.userId],
                    firstname = row[UserTable.firstname],
                    lastname = row[UserTable.lastname],
                    email = row[UserTable.email],
                    flightId = row[BookingTable.flightId],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportNameCode = departureAirportNameCode,
                    arrivalAirportNameCode = arrivalAirportNameCode,
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    status = row[BookingTable.status],
                    createdAt = row[BookingTable.createdAt],
                    aircraftType = row[AircraftTable.type]
                )
            }
    }

    /**
     * Retrieves cancelled flights using optional route and date filters.
     *
     * The result includes flight code, airport information, departure and arrival
     * times, aircraft type, and flight status.
     *
     * Param fromCodes: Optional list of departure airport codes to filter by.
     * Param toCodes: Optional list of arrival airport codes to filter by.
     * Param date: Optional departure date to filter by.
     * Return a list of cancelled flight summaries.
     */
    fun getCancelledFlights(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<CancelledFlightSummary> = transaction {
        var selectCondition: Op<Boolean> = (FlightTable.status eq FlightStatus.CANCELLED)

        if (fromCodes != null) {
            val fromIds = AirportTable.select { AirportTable.code inList fromCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable.select { AirportTable.code inList toCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }
        if (date != null) {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.atTime(23, 59, 59)
            selectCondition = selectCondition and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
        }
        
        FlightTable
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .select { selectCondition }
            .orderBy(FlightTable.departureTime, SortOrder.ASC)
            .map { row ->
                CancelledFlightSummary(
                    flightId = row[FlightTable.id],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    status = row[FlightTable.status],
                    aircraftType = row[AircraftTable.type]
                )
            }
    }

    /**
     * Updates the route and schedule information for an existing flight.
     *
     * Before updating the flight, the current departure airport, arrival airport,departure time, and arrival time are stored.
     * A change log record is record,then created so that administrators can review the history of schedule changes.
     *
     * Param flightId: The ID of the flight to update.
     * Param newDepartureAirportId: The new departure airport ID.
     * Param newArrivalAirportId: The new arrival airport ID.
     * Param newDepartureTime: The new departure date and time.
     * Param newArrivalTime: The new arrival date and time.
     * Param changedByUserId: The ID of the administrator making the change, if available.
     * Return the updated flight, or null if the flight does not exist.
     */
    fun updateFlightSchedule(flightId: Int,newDepartureAirportId: Int,newArrivalAirportId: Int,newDepartureTime: LocalDateTime,newArrivalTime: LocalDateTime, changedByUserId: Int?): Flight? = transaction {
        val existingFlight = FlightTable.select { FlightTable.id eq flightId }.singleOrNull()?: return@transaction null
        val oldDepartureAirportId = existingFlight[FlightTable.departureAirportId]
        val oldArrivalAirportId = existingFlight[FlightTable.arrivalAirportId]
        val oldDepartureTime = existingFlight[FlightTable.departureTime]
        val oldArrivalTime = existingFlight[FlightTable.arrivalTime]
        
        FlightTable.update({ FlightTable.id eq flightId }) {
            it[departureAirportId] = newDepartureAirportId
            it[arrivalAirportId] = newArrivalAirportId
            it[departureTime] = newDepartureTime
            it[arrivalTime] = newArrivalTime
        }
        FlightChangeLogTable.insert {
            it[FlightChangeLogTable.flightId] = flightId
            it[FlightChangeLogTable.oldDepartureAirportId] = oldDepartureAirportId
            it[FlightChangeLogTable.newDepartureAirportId] = newDepartureAirportId
            it[FlightChangeLogTable.oldArrivalAirportId] = oldArrivalAirportId
            it[FlightChangeLogTable.newArrivalAirportId] = newArrivalAirportId
            it[FlightChangeLogTable.oldDepartureTime] = oldDepartureTime
            it[FlightChangeLogTable.newDepartureTime] = newDepartureTime
            it[FlightChangeLogTable.oldArrivalTime] = oldArrivalTime
            it[FlightChangeLogTable.newArrivalTime] = newArrivalTime
            it[changedAt] = LocalDateTime.now()
            it[FlightChangeLogTable.changedByUserId] = changedByUserId
        }
        FlightTable
            .select { FlightTable.id eq flightId }
            .map { row -> flightRepository.resultRowToFlight(row) }
            .singleOrNull()
    }

    /**
     * Retrieves the full name of a user by ID.
     *
     * Param userId: The user ID to look up, or null.
     * Return the user's full name, or null if the ID is null or no user is found.
     */
    private fun getUserNameById(userId: Int?): String? {
        if (userId == null) return null

        return UserTable
            .select { UserTable.id eq userId }
            .map { it[UserTable.firstname] + " " + it[UserTable.lastname] }
            .singleOrNull()
    }

    /**
     * Updates the operational status of a flight.
     *
     * Param flightId: The ID of the flight to update.
     * Param newStatus: The new status to apply to the flight.
     * Return the updated flight, or null if the flight does not exist.
     */
    fun updateFlightStatus(flightId: Int, newStatus: FlightStatus): Flight? = transaction {
        val existingFlight = FlightTable
            .select { FlightTable.id eq flightId }
            .singleOrNull() ?: return@transaction null

        FlightTable.update({ FlightTable.id eq flightId }) {
            it[status] = newStatus
        }

        FlightTable
            .select { FlightTable.id eq flightId }
            .map { row -> flightRepository.resultRowToFlight(row) }
            .singleOrNull()
    }

    /**
     * Retrieves all recorded flight schedule changes using optional filters.
     *
     * The result includes the old and new route information, old and new schedule times, aircraft type, flight status, and the administrator who made the change.
     *
     * Param fromCodes: Optional list of departure airport codes to filter by.
     * Param date: Optional departure date to filter by.
     * Return a list of flight change log summaries.
     */
    fun getAllFlightChanges(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<FlightChangeLogInfo> = transaction {
        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable.select { AirportTable.code inList fromCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable.select { AirportTable.code inList toCodes }.map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }
        if (date != null) {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.atTime(23, 59, 59)
            selectCondition = selectCondition and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
        }
        (FlightChangeLogTable innerJoin FlightTable)
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .select { selectCondition }
            .orderBy(FlightChangeLogTable.changedAt, SortOrder.DESC)
            .map { row ->
                FlightChangeLogInfo(
                    id = row[FlightChangeLogTable.id],
                    flightId = row[FlightChangeLogTable.flightId],
                    flightCode = row[FlightTable.flightCode],
                    oldDepartureAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.oldDepartureAirportId]),
                    newDepartureAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.newDepartureAirportId]),
                    oldArrivalAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.oldArrivalAirportId]),
                    newArrivalAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.newArrivalAirportId]),
                    oldDepartureTime = row[FlightChangeLogTable.oldDepartureTime],
                    newDepartureTime = row[FlightChangeLogTable.newDepartureTime],
                    oldArrivalTime = row[FlightChangeLogTable.oldArrivalTime],
                    newArrivalTime = row[FlightChangeLogTable.newArrivalTime],
                    changedAt = row[FlightChangeLogTable.changedAt],
                    flightStatus = row[FlightTable.status],
                    aircraftType = row[AircraftTable.type],
                    changedByUserId = row[FlightChangeLogTable.changedByUserId],
                    changedByName = getUserNameById(row[FlightChangeLogTable.changedByUserId]),
                )
            }
    }

    /**
     * Retrieves the schedule change history for a single flight.
     *
     * Param flightId: The ID of the flight whose change history should be retrieved.
     * Return a list of change log entries for the selected flight.
     */
    fun getFlightChangesByFlightId(flightId: Int): List<FlightChangeLogInfo> = transaction {
        (FlightChangeLogTable innerJoin FlightTable)
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .select { FlightChangeLogTable.flightId eq flightId }
            .orderBy(FlightChangeLogTable.changedAt, SortOrder.DESC)
            .map { row ->
                FlightChangeLogInfo(
                    id = row[FlightChangeLogTable.id],
                    flightId = row[FlightChangeLogTable.flightId],
                    flightCode = row[FlightTable.flightCode],
                    oldDepartureAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.oldDepartureAirportId]),
                    newDepartureAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.newDepartureAirportId]),
                    oldArrivalAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.oldArrivalAirportId]),
                    newArrivalAirportNameCode = getAirportNameCodeById(row[FlightChangeLogTable.newArrivalAirportId]),
                    oldDepartureTime = row[FlightChangeLogTable.oldDepartureTime],
                    newDepartureTime = row[FlightChangeLogTable.newDepartureTime],
                    oldArrivalTime = row[FlightChangeLogTable.oldArrivalTime],
                    newArrivalTime = row[FlightChangeLogTable.newArrivalTime],
                    changedAt = row[FlightChangeLogTable.changedAt],
                    flightStatus = row[FlightTable.status],
                    aircraftType = row[AircraftTable.type],
                    changedByUserId = row[FlightChangeLogTable.changedByUserId],
                    changedByName = getUserNameById(row[FlightChangeLogTable.changedByUserId]),
                )
            }
    }

    /**
     * Formats an airport as a combined name and code string.
     *
     * Param airportId: The airport ID to look up.
     * Return a string containing the airport name and code.
     * Throws illegalStateException If the airport cannot be found.
     */
    private fun getAirportNameCodeById(airportId: Int): String {
        return AirportTable
            .select { AirportTable.id eq airportId }
            .map { it[AirportTable.name] + " " + it[AirportTable.code] }
            .singleOrNull() ?: throw IllegalStateException("Airport not found")
    }

    /**
     * Retrieves the aircraft type for a given aircraft ID.
     *
     * Param aircraftId: The aircraft ID to look up.
     * Return the aircraft type.
     * Throws illegalStateException If the aircraft cannot be found.
     */
    private fun getAircraftTypeById(aircraftId: Int): String {
        return AircraftTable
            .select { AircraftTable.id eq aircraftId }
            .map { it[AircraftTable.type] }
            .singleOrNull() ?: throw IllegalStateException("Aircraft not found")
    }

    /**
     * Each result contains booking details, customer information, flight information,payment amount where available, and aircraft type.
     *
     * Param fromCodes: Optional list of departure airport codes to filter by.
     * Param toCodes: Optional list of arrival airport codes to filter by.
     * Param date: Optional departure date to filter by.
     * Param status: Optional booking status to filter by.
     * Return a list of reservation summaries matching the filters.
     */
    fun getAllReservations(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?,status: BookingStatus?
    ): List<ReservationSummary> = transaction {

        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable.select { AirportTable.code inList fromCodes }.map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }

        if (toCodes != null) {
            val toIds = AirportTable.select { AirportTable.code inList toCodes }.map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }

        if (date != null) {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.atTime(23, 59, 59)

            selectCondition = selectCondition and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
        }

        if (status != null) {
            selectCondition = selectCondition and (BookingTable.status eq status)
        }

        BookingTable
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .join(
                UserTable,
                JoinType.INNER,
                BookingTable.userId,
                UserTable.id
            )
                .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .join(
                PaymentTable,
                JoinType.LEFT,
                BookingTable.id,
                PaymentTable.bookingId
            )
            .select { selectCondition }
            .orderBy(BookingTable.createdAt, SortOrder.DESC)
            .map { row ->
                ReservationSummary(
                    bookingId = row[BookingTable.id],
                    bookingReference = row[BookingTable.bookingReference],
                    userId = row[BookingTable.userId],
                    firstname = row[UserTable.firstname],
                    lastname = row[UserTable.lastname],
                    email = row[UserTable.email],
                    flightId = row[BookingTable.flightId],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    departureTime = row[FlightTable.departureTime],
                    bookingStatus = row[BookingTable.status],
                    createdAt = row[BookingTable.createdAt],
                    amountPaid = row.getOrNull(PaymentTable.amount),
                    aircraftType = row[AircraftTable.type]
                )
            }
    }
    
    /**
     * Finds an airport ID by its airport code.
     * The supplied airport code is normalised to uppercase before lookup.
     *
     * Param code: The airport code to search for.
     * Return the matching airport ID, or null if the airport code is not found.
     */
    fun getAirportIdByCode(code: String): Int? = transaction {
        AirportTable
            .select { AirportTable.code eq code.uppercase() }
            .map { row -> row[AirportTable.id] }
            .singleOrNull()
    }

    /**
     * Retrieves all customer flight information change requests.
     *
     * The result includes the customer, booking reference, current flight, requested flight, request type, request status, passenger changes,
     * message, admin reply,and handling timestamps.
     *
     * Return a list of flight information request summaries ordered by creation time.
     */
    fun getAllFlightInfoRequests(): List<FlightInfoRequestSummary> = transaction {
        FlightInfoRequestTable
            .join(
                BookingTable,
                JoinType.INNER,
                FlightInfoRequestTable.bookingId,
                BookingTable.id
            )
            .join(
                UserTable,
                JoinType.INNER,
                FlightInfoRequestTable.userId,
                UserTable.id
            )
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .selectAll()
            .orderBy(FlightInfoRequestTable.createdAt, SortOrder.DESC)
            .map { row ->
                FlightInfoRequestSummary(
                    id = row[FlightInfoRequestTable.id],
                    bookingReference = row[BookingTable.bookingReference],
                    userId = row[FlightInfoRequestTable.userId],
                    customerName = listOfNotNull(
                        row[UserTable.firstname],
                        row[UserTable.lastname]
                    ).joinToString(" "),
                    email = row[UserTable.email],
                    currentFlightCode = row[FlightTable.flightCode],
                    requestedFlightCode = row[FlightInfoRequestTable.requestedFlightCode],
                    requestType = row[FlightInfoRequestTable.requestType],
                    status = row[FlightInfoRequestTable.status],
                    passengerId = row[FlightInfoRequestTable.passengerId],
                    newFirstname = row[FlightInfoRequestTable.newFirstname],
                    newLastname = row[FlightInfoRequestTable.newLastname],
                    newPassportCode = row[FlightInfoRequestTable.newPassportCode],
                    message = row[FlightInfoRequestTable.message],
                    adminReply = row[FlightInfoRequestTable.adminReply],
                    createdAt = row[FlightInfoRequestTable.createdAt],
                    handledAt = row[FlightInfoRequestTable.handledAt]
                )
            }
    }
    /**
     * Retrieves customer flight information change requests byt request Id.
     *
     * The result includes the customer, booking reference, current flight, requested flight, request type, request status, passenger changes,
     * message, admin reply,and handling timestamps.
     *
     * Return flight information request summaries ordered by creation time.
     */
    fun getFlightInfoRequestsByRequestID(requestId: Int): FlightInfoRequestSummary? = transaction {
        FlightInfoRequestTable
            .join(
                BookingTable,
                JoinType.INNER,
                FlightInfoRequestTable.bookingId,
                BookingTable.id
            )
            .join(
                UserTable,
                JoinType.INNER,
                FlightInfoRequestTable.userId,
                UserTable.id
            )
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .select{FlightInfoRequestTable.id eq requestId}
            .orderBy(FlightInfoRequestTable.createdAt, SortOrder.DESC)
            .map { row ->
                FlightInfoRequestSummary(
                    id = row[FlightInfoRequestTable.id],
                    bookingReference = row[BookingTable.bookingReference],
                    userId = row[FlightInfoRequestTable.userId],
                    customerName = listOfNotNull(
                        row[UserTable.firstname],
                        row[UserTable.lastname]
                    ).joinToString(" "),
                    email = row[UserTable.email],
                    currentFlightCode = row[FlightTable.flightCode],
                    requestedFlightCode = row[FlightInfoRequestTable.requestedFlightCode],
                    requestType = row[FlightInfoRequestTable.requestType],
                    status = row[FlightInfoRequestTable.status],
                    passengerId = row[FlightInfoRequestTable.passengerId],
                    newFirstname = row[FlightInfoRequestTable.newFirstname],
                    newLastname = row[FlightInfoRequestTable.newLastname],
                    newPassportCode = row[FlightInfoRequestTable.newPassportCode],
                    message = row[FlightInfoRequestTable.message],
                    adminReply = row[FlightInfoRequestTable.adminReply],
                    createdAt = row[FlightInfoRequestTable.createdAt],
                    handledAt = row[FlightInfoRequestTable.handledAt]
                )
            }
            .singleOrNull()
    }
    fun handleFlightInfoRequest(requestId: Int,newStatus: FlightInfoRequestStatus,adminReply: String?,adminUserId: Int): Boolean = transaction {
        val request = FlightInfoRequestTable.select { FlightInfoRequestTable.id eq requestId }.singleOrNull() ?: return@transaction false

        if (newStatus == FlightInfoRequestStatus.APPROVED) {
            val requestType = request[FlightInfoRequestTable.requestType]

            if (requestType == FlightInfoRequestType.PASSENGER_INFO || requestType == FlightInfoRequestType.BOTH) {
                val passengerId = request[FlightInfoRequestTable.passengerId]

                if (passengerId != null) {
                    PassengerTable.update({ PassengerTable.id eq passengerId }) {
                        request[FlightInfoRequestTable.newFirstname]?.let { value ->
                            if (value.isNotBlank()) {
                                it[PassengerTable.firstname] = value
                            }
                        }
                        request[FlightInfoRequestTable.newLastname]?.let { value ->
                            if (value.isNotBlank()) {
                                it[PassengerTable.lastname] = value
                            }
                        }
                        request[FlightInfoRequestTable.newPassportCode]?.let { value ->
                            if (value.isNotBlank()) {
                                it[PassengerTable.passportCode] = value
                            }
                        }
                    }
                }
            }

            if (requestType == FlightInfoRequestType.FLIGHT_CHANGE || requestType == FlightInfoRequestType.BOTH) {
                val requestedFlightCode = request[FlightInfoRequestTable.requestedFlightCode]

                if (!requestedFlightCode.isNullOrBlank()) {
                    applyFlightChangeToBooking(
                        bookingId = request[FlightInfoRequestTable.bookingId],
                        requestedFlightCode = requestedFlightCode
                    )
                }
            }
        }
        FlightInfoRequestTable.update({ FlightInfoRequestTable.id eq requestId }) {
            it[FlightInfoRequestTable.status] = newStatus
            it[FlightInfoRequestTable.adminReply] = adminReply
            it[FlightInfoRequestTable.handledAt] = LocalDateTime.now()
            it[FlightInfoRequestTable.handledByUserId] = adminUserId
        }
        true
    }

    /**
     * Searches reservations by customer details or booking reference.
     *
     * The search checks customer first name, last name, email address, and booking reference. If the search contains multiple words,
     * the method also attempts to match first-name and last-name combinations in either order.
     *
     * Param query: The search keyword entered by the administrator.
     * Return a list of matching reservation summaries, or an empty list if the query is blank.
     */
    fun searchReservationsByCustomer(query: String): List<ReservationSummary> = transaction {
        val keyword = query.trim()

        if (keyword.isBlank()) {
            return@transaction emptyList()
        }
        // Split the keyword by one or more spaces, so a full name like "John Wick" can be separated into ["John", "Wick"]
        val likeKeyword = "%${keyword}%"
        val parts = keyword.split(Regex("\\s+")).filter { it.isNotBlank() }

        var searchCondition: Op<Boolean> =
            (UserTable.firstname like likeKeyword) or
            (UserTable.lastname like likeKeyword) or
            (UserTable.email like likeKeyword) or
            (BookingTable.bookingReference like likeKeyword)

        if (parts.size >= 2) {
            val first = "%${parts[0]}%"
            val second = "%${parts[1]}%"

            searchCondition = searchCondition or
                (
                    ((UserTable.firstname like first) and (UserTable.lastname like second)) or
                    ((UserTable.firstname like second) and (UserTable.lastname like first))
                )
        }

        BookingTable
            .join(
                FlightTable,
                JoinType.INNER,
                BookingTable.flightId,
                FlightTable.id
            )
            .join(
                UserTable,
                JoinType.INNER,
                BookingTable.userId,
                UserTable.id
            )
            .join(
                AircraftTable,
                JoinType.INNER,
                FlightTable.aircraftId,
                AircraftTable.id
            )
            .join(
                PaymentTable,
                JoinType.LEFT,
                BookingTable.id,
                PaymentTable.bookingId
            )
            .select { searchCondition }
            .orderBy(BookingTable.createdAt, SortOrder.DESC)
            .map { row ->
                ReservationSummary(
                    bookingId = row[BookingTable.id],
                    bookingReference = row[BookingTable.bookingReference],
                    userId = row[BookingTable.userId],
                    firstname = row[UserTable.firstname],
                    lastname = row[UserTable.lastname],
                    email = row[UserTable.email],
                    flightId = row[BookingTable.flightId],
                    flightCode = row[FlightTable.flightCode],
                    departureAirportNameCode = getAirportNameCodeById(row[FlightTable.departureAirportId]),
                    arrivalAirportNameCode = getAirportNameCodeById(row[FlightTable.arrivalAirportId]),
                    departureTime = row[FlightTable.departureTime],
                    bookingStatus = row[BookingTable.status],
                    createdAt = row[BookingTable.createdAt],
                    amountPaid = row.getOrNull(PaymentTable.amount),
                    aircraftType = row[AircraftTable.type]
                )
            }
    }

    /**
     * Applies an approved flight change request to an existing booking.
     *
     * The booking is moved to the requested flight and passenger ticket assignments
     * are updated to available seats on the new flight. If there are not enough
     * available seats, the change is rejected by throwing an exception.
     *
     * Param bookingId: The booking to update.
     * Param requestedFlightCode: The new flight code requested by the customer.
     *
     * Throws IllegalStateException If the booking, requested flight, passengers,or sufficient available seats cannot be found.
     */
    private fun applyFlightChangeToBooking(bookingId: Int, requestedFlightCode: String) {
        val bookingRow = BookingTable.select { BookingTable.id eq bookingId }.singleOrNull() ?: throw IllegalStateException("Booking not found")
        val oldFlightId = bookingRow[BookingTable.flightId]
        val newFlightRow = FlightTable.select { FlightTable.flightCode eq requestedFlightCode.uppercase() }.singleOrNull() ?: throw IllegalStateException("Requested flight not found")
        val newFlightId = newFlightRow[FlightTable.id]

        if (newFlightId == oldFlightId) {
            return
        }

        val passengerIds = PassengerTable.select { PassengerTable.bookingId eq bookingId }.map { it[PassengerTable.id] }

        if (passengerIds.isEmpty()) {
            throw IllegalStateException("No passengers found for this booking")
        }

        val bookedNewFlightSeatIds = TicketAssignmentTable
            .join(
                FlightSeatTable,
                JoinType.INNER,
                TicketAssignmentTable.flightSeatId,
                FlightSeatTable.id
            )
            .select { FlightSeatTable.flightId eq newFlightId }
            .map { it[TicketAssignmentTable.flightSeatId] }

        val availableSeatCondition =
            if (bookedNewFlightSeatIds.isEmpty()) {
                FlightSeatTable.flightId eq newFlightId
            } else {
                (FlightSeatTable.flightId eq newFlightId) and (FlightSeatTable.id notInList bookedNewFlightSeatIds)
            }

        val availableSeats = FlightSeatTable
            .join(
                SeatTable,
                JoinType.INNER,
                FlightSeatTable.seatId,
                SeatTable.id
            )
            .select { availableSeatCondition }
            .limit(passengerIds.size)
            .map { row ->
                row[FlightSeatTable.id] to row[SeatTable.seatNumber]
            }

        if (availableSeats.size < passengerIds.size) {
            throw IllegalStateException("Not enough available seats on requested flight")
        }

        BookingTable.update({ BookingTable.id eq bookingId }) {
            it[BookingTable.flightId] = newFlightId
        }
        //Pair the passenger list (passengerIds) with the available seat list (availableSeats) one by one, and then process each pair individually.
        passengerIds.zip(availableSeats).forEach { pair ->
            val passengerId = pair.first
            val newFlightSeatId = pair.second.first
            val newSeatNumber = pair.second.second

            val existingOutboundAssignmentId = TicketAssignmentTable
                .join(
                    FlightSeatTable,
                    JoinType.INNER,
                    TicketAssignmentTable.flightSeatId,
                    FlightSeatTable.id
                )
                .select {
                    (TicketAssignmentTable.passengerId eq passengerId) and (FlightSeatTable.flightId eq oldFlightId)
                }
                .map { it[TicketAssignmentTable.id] }
                .singleOrNull()

            if (existingOutboundAssignmentId != null) {
                TicketAssignmentTable.update({
                    TicketAssignmentTable.id eq existingOutboundAssignmentId
                }) {
                    it[TicketAssignmentTable.flightSeatId] = newFlightSeatId
                    it[TicketAssignmentTable.seatNumber] = newSeatNumber
                    it[TicketAssignmentTable.ticketPrice] = newFlightRow[FlightTable.minPrice]
                }
            } else {
                TicketAssignmentTable.insert {
                    it[TicketAssignmentTable.passengerId] = passengerId
                    it[TicketAssignmentTable.flightSeatId] = newFlightSeatId
                    it[TicketAssignmentTable.seatNumber] = newSeatNumber
                    it[TicketAssignmentTable.ticketPrice] = newFlightRow[FlightTable.minPrice]
                }
            }
        }
    }
   
}
