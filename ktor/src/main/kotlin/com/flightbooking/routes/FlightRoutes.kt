package com.flightbooking.routes 

import com.flightbooking.models.Flight
import com.flightbooking.repositories.FlightRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime


fun Route.flightRoutes() {

    val flightRepository = FlightRepository()

    route("/flights"){  
        get("/all"){
            val flights = flightRepository.getAllFlights()
            call.respond(flights)
        }

        get("/search"){
            val params = call.receiveParameters()

            val fromCode = params["fromCode"]
            val toCode = params["toCode"]

            if (fromCode == null || toCode == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing airport codes")
                return@get
            }

            val flights = flightRepository.searchFlights(fromCode, toCode)
            call.respond(flights)
        }
    }
}