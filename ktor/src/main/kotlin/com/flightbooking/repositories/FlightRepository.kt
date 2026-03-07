package com.flightbooking.repositories

import com.flightbooking.models.Flight
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.AirportTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class FlightRepository {

    fun getAllFlights(): List<Flight> = transaction {
        FlightTable.selectAll()
        .map { resultRowToFlight(it) }
    }

    fun searchFlights(fromCode: String, toCode: String): List<Flight> = transaction {
        val from = AirportTable
        .select { AirportTable.code eq fromCode }
        .singleOrNull()
        
        val to = AirportTable
        .select { AirportTable.code eq toCode }
        .singleOrNull()

        if (from == null || to == null){
            return@transaction emptyList()
        } 

        val fromId = from[AirportTable.id]
        val toId = to[AirportTable.id]

        FlightTable
        .select { (FlightTable.departureAirportId eq fromId) and (FlightTable.arrivalAirportId eq toId) }
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