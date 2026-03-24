package com.flightbooking.repositories

import com.flightbooking.models.Flight
import com.flightbooking.models.Airport
import com.flightbooking.models.FlightInfo
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.TicketAssignmentTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import java.time.LocalDateTime
import java.time.LocalDate

class FlightRepository {

    fun searchFlights(fromCodes: List<String>?, toCodes: List<String>?, date: LocalDate?, numOfPassengers: Int?): List<FlightInfo> = transaction {

        val searchFromCodes = fromCodes ?: listOf("LBA")
        val searchDate = date ?: LocalDate.now()
        val searchNumOfPassengers = numOfPassengers ?: 1

        val fromIds = AirportTable
            .select { AirportTable.code inList searchFromCodes }
            .map { it[AirportTable.id] }

        val dayStart = searchDate.atStartOfDay()
        val dayEnd = searchDate.atTime(23, 59, 59)
        
        val availableFlightIds = (FlightSeatTable leftJoin TicketAssignmentTable)
            .slice(FlightSeatTable.flightId)
            .selectAll()
            .groupBy(FlightSeatTable.flightId)
            .having { (FlightSeatTable.id.count() - TicketAssignmentTable.id.count()) greaterEq searchNumOfPassengers.toLong() }
            .map { it[FlightSeatTable.flightId] }
        
        var selectCondition = (FlightTable.id inList availableFlightIds) and 
            (FlightTable.departureAirportId inList fromIds) and 
            (FlightTable.departureTime greaterEq dayStart) and
            (FlightTable.departureTime lessEq dayEnd)

        if (toCodes != null) {
            val toIds = AirportTable
                .select { AirportTable.code inList toCodes }
                .map { it[AirportTable.id] }

            selectCondition = selectCondition and (FlightTable.arrivalAirportId inList toIds)
        }
                
        FlightTable
                .select { selectCondition }
                .map { 
                    val depAirport = getAirportById(it[FlightTable.departureAirportId])
                    val arrAirport = getAirportById(it[FlightTable.arrivalAirportId])
                    FlightInfo(
                        flightCode = it[FlightTable.flightCode],
                        departureAirport = depAirport.name,
                        departureAirportCode = depAirport.code,
                        arrivalAirport = arrAirport.name,
                        arrivalAirportCode = arrAirport.code,
                        departureTime = it[FlightTable.departureTime],
                        priceFrom = it[FlightTable.minPrice]
                    )
                }
    }

    fun getAirportById(airportId: Int): Airport  = transaction {
        AirportTable
            .select { AirportTable.id eq airportId }
            .map { resultRowToAirport(it) }.singleOrNull()  ?: throw IllegalStateException("Airport not found")
    } 


    fun getFlightByFlightCode(flightCode: String): Flight? = transaction {
        FlightTable
            .select { FlightTable.flightCode eq flightCode }
            .map { resultRowToFlight(it) }.singleOrNull()
    }

    fun getFlightByFlightId(flightId: Int): Flight? = transaction {
        FlightTable
            .select { FlightTable.id eq flightId }
            .map { resultRowToFlight(it) }.singleOrNull()
    }
    
    fun getAirportBySearch(search: String): List<Airport>  = transaction {
        AirportTable
            .select { (AirportTable.name like "%$search%") or
                    (AirportTable.code like "$search%") or
                    (AirportTable.city like "%$search%") or
                    (AirportTable.country like "%$search%")
            }
            .limit(10)
            .map { resultRowToAirport(it) }
    }

    fun resultRowToAirport(row: ResultRow): Airport {
        return Airport(
            id = row[AirportTable.id],
            name = row[AirportTable.name],
            code = row[AirportTable.code],
            city = row[AirportTable.city],
            country = row[AirportTable.country]
        )
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
            minPrice = row[FlightTable.minPrice],
            status = row[FlightTable.status]
        )
    }
}
