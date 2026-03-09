package com.flightbooking.repositories

import com.flightbooking.models.Flight
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.AirportTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.LocalDate

class FlightRepository {

    fun searchFlights(fromCodes: List<String>?, toCodes: List<String>?, date: LocalDate?, numOfPassengers: Int?): List<Flight> = transaction {

        val searchFromCodes = fromCodes ?: listOf("LBA")
        val searchDate = date ?: LocalDate.now()
        val searchNumOfPassengers = numOfPassengers ?: 1

        val fromIds = AirportTable
        .select { AirportTable.code inList searchFromCodes }
        .map { it[AirportTable.id] }

        val dayStart = searchDate.atStartOfDay()
        val dayEnd = searchDate.atTime(23, 59, 59)

        if (toCodes == null) {
            FlightTable
            .select { 
                (FlightTable.departureAirportId inList fromIds) and 
                (FlightTable.departureTime greaterEq dayStart) and
                (FlightTable.departureTime lessEq dayEnd)
            }
            .map { resultRowToFlight(it) }
        }
        else {
            val toIds = AirportTable
            .select { AirportTable.code inList toCodes }
            .map { it[AirportTable.id] }

            FlightTable
            .select { 
                (FlightTable.departureAirportId inList fromIds) and 
                (FlightTable.arrivalAirportId inList toIds) and
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

