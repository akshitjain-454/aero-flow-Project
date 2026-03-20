package com.flightbooking.routes

import com.flightbooking.models.Flight
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.respondPebble
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Route.flightRoutes() {

    val flightRepository = FlightRepository()

    get("/flights") {
        val params = call.request.queryParameters
        val origin = params["origin"]
        val destination = params["destination"]
        val date = params["date"]?.let { LocalDate.parse(it) }
        val numOfPassengers = params["numOfPassengers"]?.toIntOrNull() ?: 1

        // Extract airport codes from strings like "London (LHR) — United Kingdom"
        val fromCode = origin?.let { extractCode(it) }
        val toCode = destination?.let { extractCode(it) }

        val flights = flightRepository.searchFlights(
            fromCodes = if (fromCode != null) listOf(fromCode) else null,
            toCodes = if (toCode != null) listOf(toCode) else null,
            date = date,
            numOfPassengers = numOfPassengers
        )

        // Fetch airport info for display
        val allAirportIds = flights.flatMap { listOf(it.departureAirportId, it.arrivalAirportId) }.distinct()
        val airports = flightRepository.getAirportsByIds(allAirportIds).associateBy { it.id }

        call.respondPebble("flights.peb", mapOf(
            "flights" to flights,
            "airports" to airports,
            "origin" to (origin ?: ""),
            "destination" to (destination ?: ""),
            "date" to (params["date"] ?: ""),
            "numOfPassengers" to numOfPassengers,
            "noResults" to flights.isEmpty()
        ))
    }

    get("/search") {
        val params = call.request.queryParameters
        val fromCodes = call.request.queryParameters.getAll("from")
        val toCodes = call.request.queryParameters.getAll("to")
        val date = params["date"]?.let { LocalDate.parse(it) }
        val numOfPassengers = params["numOfPassengers"]?.toIntOrNull()

        val flights = flightRepository.searchFlights(fromCodes, toCodes, date, numOfPassengers)
        call.respond(flights)
    }

    get("/airports") {
        val search = call.request.queryParameters["search"] ?: ""
        if (search.length < 2) {
            call.respond(emptyList<String>())
            return@get
        }
        val airports = flightRepository.getAirportBySearch(search)
        call.respond(airports)
    }

    get("/airports/search") {
        val search = call.request.queryParameters["search"] ?: ""
        if (search.length < 2) {
            call.respond(emptyList<String>())
            return@get
        }
        val airports = flightRepository.getAirportBySearch(search)
        call.respondPebble("index.peb", mapOf("airports" to airports))
    }
    
}

// Extracts "LHR" from "London (LHR) — United Kingdom"
private fun extractCode(input: String): String? {
    val match = Regex("\\(([A-Z]{2,4})\\)").find(input)
    return match?.groupValues?.get(1)
}