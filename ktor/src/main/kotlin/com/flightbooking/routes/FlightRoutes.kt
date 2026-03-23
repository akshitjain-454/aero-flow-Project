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
        val date = try {
                        params["date"]?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                    }
                    catch (e: Exception) {
                        return@get call.respondPebble("index.peb", mapOf("error" to "Invalid date format"))
                    }
        val numOfPassengers = params["numOfPassengers"]?.toIntOrNull()

        val flightsInfo = flightRepository.searchFlights(fromCodes, toCodes, date, numOfPassengers)
        //call.respond(flightsInfo)
        call.respondPebble("flightsearch.peb", mapOf("flightsInfo" to flightsInfo))
        call.respond(flightsInfo)
        call.respondPebble("flightsearch.peb", mapOf("flightsInfo" to flightsInfo))
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
        val searchField = call.request.queryParameters["field"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing field parameter")
        val fromValue = call.request.queryParameters["from"] ?: ""
        val toValue = call.request.queryParameters["to"] ?: ""
        val dateValue = call.request.queryParameters["date"] ?: ""
        val numOfPassengersValue = call.request.queryParameters["numOfPassengers"] ?: ""
       
        if(searchField != "from" && searchField != "to") {
            return@get call.respond(HttpStatusCode.BadRequest, "Incorrect field parameter")
        }

        if(search.length < 2) {
            call.respondPebble("index.peb", mapOf(
                "airports" to emptyList<String>(),
                "field" to searchField,
                "fromValue" to fromValue,
                "toValue" to toValue,
                "dateValue" to dateValue,
                "numOfPassengersValue" to numOfPassengersValue
            ))
            return@get
        }

        val airports = flightRepository.getAirportBySearch(search)
        
        call.respondPebble("index.peb", mapOf(
            "airports" to airports,
            "field" to searchField,
            "fromValue" to fromValue,
            "toValue" to toValue,
            "dateValue" to dateValue,
            "numOfPassengersValue" to numOfPassengersValue
        )) //returns airports to be used in front end to display in search bar.
    }
}