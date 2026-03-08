package com.flightbooking.routes 

import com.flightbooking.models.Flight
import com.flightbooking.repositories.FlightRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.LocalDate


fun Route.flightRoutes() {

    val flightRepository = FlightRepository()

    get("/search"){
        val params = call.request.queryParameters

        val fromCode = params["fromCode"]
        val toCode = params["toCode"]
        val date = params["date"]?.let { LocalDate.parse(it) }
        val numOfPassengers = params["numOfPassengers"]?.toIntOrNull()

        val flights = flightRepository.searchFlights(fromCode, toCode, date, numOfPassengers)
        call.respond(flights)
    }
}