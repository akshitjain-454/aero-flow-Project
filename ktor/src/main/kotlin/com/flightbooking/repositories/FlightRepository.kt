package com.flightbooking.repositories

import com.flightbooking.models.Airport
import com.flightbooking.models.Flight
import com.flightbooking.models.FlightInfo
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.TicketAssignmentTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime

private val DAY_END_TIME = LocalTime.MAX
private val DEFAULT_FROM_AIRPORT_CODES = listOf("LBA")
private const val DEFAULT_PASSENGER_COUNT = 1
private const val DEFAULT_DEPARTURE_FLEXIBILITY = 0L
private const val AIRPORT_SEARCH_LIMIT = 10

/**
 * Provides flight search and lookup operations.
 *
 * This repository is responsible for searching available flights, resolving airport details,
 * and converting database rows into flight and airport models.
 */
class FlightRepository {
    /**
     * Searches available flights based on route, date, passenger count, and flexibility.
     *
     * @param fromCodes Optional list of departure airport codes.
     * @param toCodes Optional list of arrival airport codes.
     * @param date Optional travel date.
     * @param numOfPassengers Optional number of passengers required.
     * @param departureFlexibility Optional date flexibility in days.
     * @return A list of matching flight information objects.
     */
    fun searchFlights(
        fromCodes: List<String>?,
        toCodes: List<String>?,
        date: LocalDate?,
        numOfPassengers: Int?,
        departureFlexibility: Long?,
    ): List<FlightInfo> =
        transaction {
            val searchFromCodes = fromCodes ?: DEFAULT_FROM_AIRPORT_CODES
            val searchDate = date ?: LocalDate.now()
            val searchNumOfPassengers = numOfPassengers ?: DEFAULT_PASSENGER_COUNT
            val searchDepartureFlexibility = departureFlexibility ?: DEFAULT_DEPARTURE_FLEXIBILITY

            val fromIds =
                AirportTable
                    .select { AirportTable.code inList searchFromCodes }
                    .map { it[AirportTable.id] }

            var dayStart = searchDate.atStartOfDay()
            dayStart = dayStart.minusDays(searchDepartureFlexibility)
            var dayEnd = searchDate.atTime(DAY_END_TIME)
            dayEnd = dayEnd.plusDays(searchDepartureFlexibility)

            val availableFlightIds =
                (FlightSeatTable leftJoin TicketAssignmentTable)
                    .slice(FlightSeatTable.flightId)
                    .selectAll()
                    .groupBy(FlightSeatTable.flightId)
                    .having { (FlightSeatTable.id.count() - TicketAssignmentTable.id.count()) greaterEq searchNumOfPassengers.toLong() }
                    .map { it[FlightSeatTable.flightId] }

            var selectCondition =
                (FlightTable.id inList availableFlightIds) and
                    (FlightTable.departureAirportId inList fromIds) and
                    (FlightTable.departureTime greaterEq dayStart) and
                    (FlightTable.departureTime lessEq dayEnd)

            if (toCodes != null) {
                val toIds =
                    AirportTable
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
                        priceFrom = it[FlightTable.minPrice],
                    )
                }
        }

    /**
     * Retrieves airport details by airport ID.
     *
     * @param airportId The ID of the airport.
     * @return The airport model.
     */
    fun getAirportById(airportId: Int): Airport =
        transaction {
            AirportTable
                .select { AirportTable.id eq airportId }
                .map { resultRowToAirport(it) }.singleOrNull() ?: throw IllegalStateException("Airport not found")
        }

    /**
     * Finds a flight by its flight code.
     *
     * @param flightCode The flight code to search.
     * @return The matching flight, or null if none exists.
     */
    fun getFlightByFlightCode(flightCode: String): Flight? =
        transaction {
            FlightTable
                .select { FlightTable.flightCode eq flightCode }
                .map { resultRowToFlight(it) }.singleOrNull()
        }

    /**
     * Finds a flight by its unique flight ID.
     *
     * @param flightId The flight ID to search.
     * @return The matching flight, or null if not found.
     */
    fun getFlightByFlightId(flightId: Int): Flight? =
        transaction {
            FlightTable
                .select { FlightTable.id eq flightId }
                .map { resultRowToFlight(it) }.singleOrNull()
        }

    /**
     * Searches airports by name, code, city, or country prefix.
     *
     * @param search The search string used for matching airport fields.
     * @return A list of airports that match the search.
     */
    fun getAirportBySearch(search: String): List<Airport> =
        transaction {
            AirportTable
                .select {
                    (AirportTable.name like "$search%") or
                        (AirportTable.code like "$search%") or
                        (AirportTable.city like "$search%") or
                        (AirportTable.country like "$search%")
                }
                .limit(AIRPORT_SEARCH_LIMIT)
                .map { resultRowToAirport(it) }
        }

    /**
     * Maps a database row to an Airport model.
     *
     * @param row The result row containing airport fields.
     * @return The airport model.
     */
    fun resultRowToAirport(row: ResultRow): Airport {
        return Airport(
            id = row[AirportTable.id],
            name = row[AirportTable.name],
            code = row[AirportTable.code],
            city = row[AirportTable.city],
            country = row[AirportTable.country],
        )
    }

    /**
     * Maps a database row to a Flight model.
     *
     * @param row The result row containing flight fields.
     * @return The flight model.
     */
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
            status = row[FlightTable.status],
        )
    }
}
