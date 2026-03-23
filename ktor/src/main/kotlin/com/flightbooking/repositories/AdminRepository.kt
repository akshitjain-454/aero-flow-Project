package com.flightbooking.repositories

import com.flightbooking.enums.BookingStatus
import com.flightbooking.models.BookingsPerFlightReport
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

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
}