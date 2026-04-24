package com.flightbooking.repositories

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.models.BookingsPerFlightReport
import com.flightbooking.models.CancelledBookingSummary
import com.flightbooking.models.FlightAvailabilitySummary
import com.flightbooking.models.FlightChangeLogInfo
import com.flightbooking.models.Flight
import com.flightbooking.models.CancelledFlightSummary
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.FlightChangeLogTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.UserTable
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
            .slice(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
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
                FlightTable.status
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
            bookingCount = bookingCount
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
            .slice(
                FlightTable.id,
                FlightTable.flightCode,
                FlightTable.departureAirportId,
                FlightTable.arrivalAirportId,
                FlightTable.departureTime,
                FlightTable.arrivalTime,
                FlightTable.status,
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
                FlightTable.status
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
                    status = row[BookingTable.status],
                    createdAt = row[BookingTable.createdAt]
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
                    status = row[FlightTable.status]
                )
            }
    }

    fun updateFlightSchedule(flightId: Int,newDepartureAirportId: Int,newArrivalAirportId: Int,newDepartureTime: LocalDateTime,newArrivalTime: LocalDateTime): Flight? = transaction {
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
                    changedAt = row[FlightChangeLogTable.changedAt]
                )
            }
    }

    fun getFlightChangesByFlightId(flightId: Int): List<FlightChangeLogInfo> = transaction {
        (FlightChangeLogTable innerJoin FlightTable)
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
                    changedAt = row[FlightChangeLogTable.changedAt]
                )
            }
    }

    private fun getAirportNameCodeById(airportId: Int): String {
        return AirportTable
            .select { AirportTable.id eq airportId }
            .map { it[AirportTable.name] + " " + it[AirportTable.code] }
            .singleOrNull() ?: throw IllegalStateException("Airport not found")
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
                    amountPaid = row.getOrNull(PaymentTable.amount)
                )
            }
    }

    
}
