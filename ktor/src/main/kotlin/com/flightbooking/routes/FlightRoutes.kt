package com.flightbooking.routes 

import com.flightbooking.models.Flight
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.respondPebble
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.LocalDate


fun Route.flightRoutes() {

    val flightRepository = FlightRepository()

    get("/search") {
        val params = call.request.queryParameters

        val fromCodes = call.request.queryParameters.getAll("from")
        val toCodes = call.request.queryParameters.getAll("to")
        val date = params["date"]?.let { LocalDate.parse(it) }
        val numOfPassengers = params["numOfPassengers"]?.toIntOrNull()

        val flights = flightRepository.searchFlights(fromCodes, toCodes, date, numOfPassengers)
        call.respond(flights)
        //call.respondPebble("flightsearch.peb", mapOf("flights" to flights))
    }
    get("/airports") {
        val search = call.request.queryParameters["search"] ?: ""
       
        if(search.length < 2) {
            call.respond(emptyList<String>())
            return@get
        }

        val airports = flightRepository.getAirportBySearch(search)

        call.respond(airports)
    }
    get("/airports/search") { //Used when js isn't available so search button needed 
        val search = call.request.queryParameters["search"] ?: "" 
       
        if(search.length < 2) {
            call.respond(emptyList<String>())
            return@get
        }

        val airports = flightRepository.getAirportBySearch(search)
        
        call.respondPebble("index.peb", mapOf("airports" to airports)) //returns airports to be used in front end to display in search bar.
    }
}