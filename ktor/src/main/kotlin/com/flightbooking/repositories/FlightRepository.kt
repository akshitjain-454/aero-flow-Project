package com.flightbooking.repositories

import com.flightbooking.models.Flight
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.AirportTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.LocalDate

class FlightRepository {

    fun searchFlights(fromCode: String?, toCode: String?, date: LocalDate?, numOfPassengers: Int?): List<Flight> = transaction {

        val searchFromCode = fromCode ?: "LBA"
        val searchDate = date ?: LocalDate.now()
        val searchNumOfPassengers = numOfPassengers ?: 1

        val from = AirportTable
        .select { AirportTable.code eq searchFromCode }
        .singleOrNull() ?: return@transaction emptyList()

        val fromId = from[AirportTable.id]

        val dayStart = searchDate.atStartOfDay()
        val dayEnd = searchDate.atTime(23, 59, 59)

        if (toCode == null) {
            FlightTable
            .select { 
                (FlightTable.departureAirportId eq fromId) and 
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
            }
            .map { resultRowToFlight(it) }
        }
        else {
            val to = AirportTable
            .select { AirportTable.code eq toCode }
            .singleOrNull() ?: return@transaction emptyList()

            val toId = to[AirportTable.id]

            FlightTable
            .select { 
                (FlightTable.departureAirportId eq fromId) and 
                (FlightTable.arrivalAirportId eq toId) and
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
            }
            .map { resultRowToFlight(it) }
        }
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

