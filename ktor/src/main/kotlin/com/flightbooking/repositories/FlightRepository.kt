package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class FlightRepository {

    fun getAllFlights(): List<Flight> = transaction {
        FlightTable.selectAll()
        .map { resultRowToFlight(it) }
    }
    fun resultRowToFlight(row: ResultRow): Flight {
        return Flight(
            id = row[FlightTable.id],
            flightCode = row[FlightTable.flightCode],
            departureAirportId = row[FlightTable.departureAirportId],
            arrivalAirportId = row[FlightTable.arrivalAirportId],
            aircraftId = row[FlightTable.aircraftId],
            departureTime = row[FlightTable.departureTime],
            arrivalTime = row[FlightTable.arrivalTime],
            price = row[FlightTable.price],
            status = row[FlightTable.status]
        )
    }
}

