package com.flightbooking.routes 

import com.flightbooking.repositories.BookingRepository
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.sessions.UserSession
import io.ktor.server.sessions.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Route.bookingRoutes() {

    val bookingRepository = BookingRepository()
    val flightRepository = FlightRepository()

    route("/booking"){

        post("/create_booking"){
            val params = call.receiveParameters()
            val flightCode = params["flight_code"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing flight_code")
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login") //Need this to be the page btw not the login post. TODO - make page loading routes

            val flight = flightRepository.getFlightByFlightCode(flightCode) ?: return@post call.respond(HttpStatusCode.NotFound, "Flight not found")
            val flightId = flight.id

            val booking = bookingRepository.createBooking(session.userId, flightId)
            
            call.respondRedirect("/booking/${booking.bookingReference}/passengers")
        }
        post("/{reference}/passengers"){
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()

            val booking = bookingRepository.filterBooking(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Unauthorized, "Not the users booking") 
            }

            val firstname = params["firstname"] 
            val lastname = params["lastname"]
            val passportCode = params["passportCode"]

            if (firstname == null || lastname == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing reference, firstname or lastname")
                return@post
            }

            val passenger = bookingRepository.addPassenger(booking.id, firstname, lastname, passportCode)

            call.respondRedirect("/booking/$reference/passengers")
        }
    }
}