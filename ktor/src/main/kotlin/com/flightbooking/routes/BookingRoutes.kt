package com.flightbooking.routes 

import com.flightbooking.repositories.BookingRepository
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.repositories.UserRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.SeatClass
import com.flightbooking.respondPebble
import io.ktor.server.sessions.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal

fun Route.bookingRoutes() {

    val bookingRepository = BookingRepository()
    val flightRepository = FlightRepository()
    val userRepository = UserRepository()

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
        get("/{reference}/seats") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")

            if(booking.userId != session.userId) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            val seats = bookingRepository.getSeatsByFlightId(booking.flightId)
            
            call.respond(seats)
        }

        post("/{reference}/ticket_assignment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            val flight = flightRepository.getFlightByFlightId(booking.flightId) ?: return@post call.respond(HttpStatusCode.NotFound, "Flight not found")

            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }
            
            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            for(passenger in passengers) {
                val flightSeatId = params["passenger_${passenger.id}_seat_id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing flight seat id for passenger ${passenger.id}")
                val seatClassParam = params["passenger_${passenger.id}_seat_class"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing seat class for passenger ${passenger.id}")
                val seatClass = SeatClass.valueOf(seatClassParam)
                val seatNumber = params["passenger_${passenger.id}_seat_number"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing seat number for passenger ${passenger.id}")
                val date = flight.departureTime.toLocalDate()
                val ticketPrice = bookingRepository.calculatePrice(flight.minPrice, seatClass, date)

                try {
                    bookingRepository.ticketAssignment(passenger.id, flightSeatId, ticketPrice, seatNumber)
                }
                catch(e: Exception) {
                    return@post call.respond(HttpStatusCode.Conflict, "Seat already taken") // Shouldn't be possible to select
                }
            }
            
            call.respondRedirect("/booking/$reference/payment")
        }

        get("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")
            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            var price = BigDecimal.ZERO
            for(passenger in passengers){
                val ticketPrice = bookingRepository.getTicketPriceByPassengerId(passenger.id) ?: return@get call.respond(HttpStatusCode.NotFound, "Ticket price not found")
                price = price.add(ticketPrice)
            }

            call.respond(price)
            //call.respondPebble("payment.peb", mapOf("price" to price))
        }

        post("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")

            val amountParam = params["amount"] ?: return@post call.respond(HttpStatusCode.BadRequest, "No amount paid")
            val amount = BigDecimal(amountParam)
            val paymentMethodParam = params["payment_method"]
            val paymentMethod = PaymentMethod.valueOf(paymentMethodParam!!)
            val payment = bookingRepository.createPayment(booking.id, amount, paymentMethod)
            call.respond(payment)
            //call.respondPebble("paymentConfirmation.peb", mapOf("payment" to payment))
        }

        post("/{reference}/send_tickets") { //button on paymentConfirmation.peb
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respond(HttpStatusCode.NotFound, "Logged in user not found")

            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            val ticketInfo = passengers.map { bookingRepository.getTicketInfoByPassengerAndBooking(it, booking) }

            for(ticket in ticketInfo) {
                userRepository.sendEmail(
                    email = user.email,
                    subject = "Your Aero-Flow Ticket — ${ticket.bookingReference}",
                    body = """
                        Passenger: ${ticket.passengerName}
                        From: ${ticket.departureAirport}
                        To: ${ticket.arrivalAirport}
                        Departure: ${ticket.dateTime}
                        Seat: ${ticket.seatNumber}
                        Booking Reference: ${ticket.bookingReference}
                    """.trimIndent()
                )
            }
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