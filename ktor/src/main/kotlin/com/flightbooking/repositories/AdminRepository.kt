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

class AdminRepository {

    private val flightRepository = FlightRepository()

    fun getBookingsPerFlightReport(): List<BookingsPerFlightReport> = transaction {
        val bookingCountExpr = BookingTable.id.count()

        (BookingTable innerJoin FlightTable)
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
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    flightStatus = row[FlightTable.status],
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
            departureTime = flight.departureTime,
            arrivalTime = flight.arrivalTime,
            flightStatus = flight.status,
            bookingCount = bookingCount
        )
    }

    fun getFlightAvailabilityReport(): List<FlightAvailabilitySummary> = transaction {
        val totalSeatsExpr = FlightSeatTable.id.count()
        val bookedSeatsExpr = TicketAssignmentTable.id.count()

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
            .selectAll()
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
                    departureTime = row[FlightTable.departureTime],
                    arrivalTime = row[FlightTable.arrivalTime],
                    flightStatus = row[FlightTable.status],
                    totalSeats = totalSeats,
                    bookedSeats = bookedSeats,
                    availableSeats = totalSeats - bookedSeats
                )
            }
    }

    // fun getCancelledBookings(): List<CancelledBookingSummary> = transaction {
    //     BookingTable
    //         .select { BookingTable.status eq BookingStatus.CANCELLED }
    //         .orderBy(BookingTable.createdAt, SortOrder.DESC)
    //         .map { row ->
    //             CancelledBookingSummary(
    //                 bookingId = row[BookingTable.id],
    //                 bookingReference = row[BookingTable.bookingReference],
    //                 userId = row[BookingTable.userId],
    //                 flightId = row[BookingTable.flightId],
    //                 createdAt = row[BookingTable.createdAt]
    //             )
    //         }
    // }
    
    fun getCancelledBookings(): List<CancelledBookingSummary> = transaction {
        (BookingTable innerJoin FlightTable innerJoin UserTable)
            .select { BookingTable.status eq BookingStatus.CANCELLED }
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

    fun getCancelledFlights(): List<CancelledFlightSummary> = transaction {
        FlightTable
            .select { FlightTable.status eq FlightStatus.CANCELLED }
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

    fun getAllFlightChanges(): List<FlightChangeLogInfo> = transaction {
        (FlightChangeLogTable innerJoin FlightTable)
            .selectAll()
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

}