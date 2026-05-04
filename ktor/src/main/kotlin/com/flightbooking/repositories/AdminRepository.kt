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
import org.jetbrains.exposed.sql.TextColumnType
import com.flightbooking.models.ReservationSummary
import com.flightbooking.tables.PaymentTable
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.models.FlightInfoRequestSummary
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList

class AdminRepository {

    private val flightRepository = FlightRepository()

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

    fun getPeakBookingTimesReport(): List<PeakBookingTimeReport> = transaction {

        val bookingCountExpr = BookingTable.id.count()
        //This is the SQLite time formatting function strftime  change 2026-03-25 14:37:22 in 14:00
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

    fun getFlightAvailabilityReport(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<FlightAvailabilitySummary> = transaction {
        val totalSeatsExpr = FlightSeatTable.id.count()
        val bookedSeatsExpr = TicketAssignmentTable.id.count()
        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable
                .select { AirportTable.code inList fromCodes }
                .map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }

        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }

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

    fun getCancelledBookings(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<CancelledBookingSummary> = transaction {
        var selectCondition: Op<Boolean> = (BookingTable.status eq BookingStatus.CANCELLED)

        if (fromCodes != null) {
            val fromIds = AirportTable
                .select { AirportTable.code inList fromCodes }
                .map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }
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

    fun getCancelledFlights(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<CancelledFlightSummary> = transaction {
        var selectCondition: Op<Boolean> = (FlightTable.status eq FlightStatus.CANCELLED)

        if (fromCodes != null) {
            val fromIds = AirportTable
                .select { AirportTable.code inList fromCodes }
                .map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }
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

    fun updateFlightSchedule(flightId: Int,newDepartureAirportId: Int,newArrivalAirportId: Int,newDepartureTime: LocalDateTime,newArrivalTime: LocalDateTime, changedByUserId: Int?): Flight? = transaction {
        val existingFlight = FlightTable
            .select { FlightTable.id eq flightId }
            .singleOrNull()?: return@transaction null
        
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

    private fun getUserNameById(userId: Int?): String? {
        if (userId == null) return null

        return UserTable
            .select { UserTable.id eq userId }
            .map { it[UserTable.firstname] + " " + it[UserTable.lastname] }
            .singleOrNull()
    }

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

    fun getAllFlightChanges(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?): List<FlightChangeLogInfo> = transaction {
        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable
                .select { AirportTable.code inList fromCodes }
                .map { it[AirportTable.id] }
            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }
        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }
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

    private fun getAirportNameCodeById(airportId: Int): String {
        return AirportTable
            .select { AirportTable.id eq airportId }
            .map { it[AirportTable.name] + " " + it[AirportTable.code] }
            .singleOrNull() ?: throw IllegalStateException("Airport not found")
    }

    private fun getAircraftTypeById(aircraftId: Int): String {
        return AircraftTable
            .select { AircraftTable.id eq aircraftId }
            .map { it[AircraftTable.type] }
            .singleOrNull() ?: throw IllegalStateException("Aircraft not found")
    }

    fun getAllReservations(fromCodes: List<String>?,toCodes: List<String>?,date: LocalDate?,status: BookingStatus?
    ): List<ReservationSummary> = transaction {

        var selectCondition: Op<Boolean> = Op.TRUE

        if (fromCodes != null) {
            val fromIds = AirportTable
                .select { AirportTable.code inList fromCodes }
                .map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.departureAirportId inList fromIds)
        }

        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }

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

    fun getAirportIdByCode(code: String): Int? = transaction {
        AirportTable
            .select { AirportTable.code eq code.uppercase() }
            .map { row -> row[AirportTable.id] }
            .singleOrNull()
    }

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

    fun handleFlightInfoRequest(requestId: Int,newStatus: FlightInfoRequestStatus,adminReply: String?,adminUserId: Int): Boolean = transaction {
        val request = FlightInfoRequestTable
            .select { FlightInfoRequestTable.id eq requestId }
            .singleOrNull() ?: return@transaction false

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

    private fun applyFlightChangeToBooking(bookingId: Int, requestedFlightCode: String) {
        val bookingRow = BookingTable
            .select { BookingTable.id eq bookingId }
            .singleOrNull() ?: throw IllegalStateException("Booking not found")

        val oldFlightId = bookingRow[BookingTable.flightId]

        val newFlightRow = FlightTable
            .select { FlightTable.flightCode eq requestedFlightCode.uppercase() }
            .singleOrNull() ?: throw IllegalStateException("Requested flight not found")

        val newFlightId = newFlightRow[FlightTable.id]

        if (newFlightId == oldFlightId) {
            return
        }

        val passengerIds = PassengerTable
            .select { PassengerTable.bookingId eq bookingId }
            .map { it[PassengerTable.id] }

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
