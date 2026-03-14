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

    route("/booking") {

        post("/create_booking") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login") //Need this to be the page btw not the login post. TODO - make page loading routes
            val params = call.receiveParameters()
            val flightCode = params["flight_code"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing flight_code")

            val flight = flightRepository.getFlightByFlightCode(flightCode) ?: return@post call.respond(HttpStatusCode.NotFound, "Flight not found")
            val flightId = flight.id

            val booking = bookingRepository.createBooking(session.userId, flightId)
            
            call.respondRedirect("/booking/${booking.bookingReference}/passengers")
        }

        post("/{reference}/passengers") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()

            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            val firstname = params["firstname"] 
            val lastname = params["lastname"]
            val passportCode = params["passportCode"]

            if (firstname == null || lastname == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing reference, firstname or lastname")
                return@post
            }

            bookingRepository.addPassenger(booking.id, firstname, lastname, passportCode)

            call.respondRedirect("/booking/$reference/passengers")
        }

        post("/{reference}/seat_and_ticket_assignment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference)?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")

            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }
            
            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            for(passenger in passengers) {
                val flightSeatId = params["passenger_${passenger.id}"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing seat for passenger ${passenger.id}")
                
                val taken = bookingRepository.isSeatTaken(flightSeatId)
                if(taken) { return@post call.respond(HttpStatusCode.Conflict, "Seat already taken") }
                
                bookingRepository.ticketAssignment(passenger.id, flightSeatId) ?: return@post call.respond(HttpStatusCode.InternalServerError, "Could not assign ticket")
            }
            
            call.respondRedirect("/booking/$reference/payment")
        }

        post("/{reference}/cancel") {
            val session = call.sessions.get<UserSession>()?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"]?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference)?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if (booking.userId != session.userId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Not the user's booking")
                }
            
            val cancelledBooking = bookingRepository.cancelBooking(reference) ?: return@post call.respond(HttpStatusCode.InternalServerError, "Could not cancel booking")
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Booking cancelled successfully",
                    "bookingReference" to cancelledBooking.bookingReference,
                    "status" to cancelledBooking.status.toString()
                )
            )
        }
    }

    get("/review_bookings") {
        val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
        val bookings = bookingRepository.getBookingsByUserId(session.userId)

        call.respond(bookings)
    }
}