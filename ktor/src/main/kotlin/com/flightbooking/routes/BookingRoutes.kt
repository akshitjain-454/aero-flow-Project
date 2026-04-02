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
import java.time.LocalDateTime

fun Route.bookingRoutes() {

    val bookingRepository = BookingRepository()
    val flightRepository = FlightRepository()
    val userRepository = UserRepository()

    route("/booking") {

        post("/create_booking") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login") //Need this to be the page btw not the login post. TODO - make page loading routes
            val params = call.receiveParameters()
            val flightCode = params["flight_code"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing flight_code")
            val returnFlightCode = params["return_flight_code"]

            val flight = flightRepository.getFlightByFlightCode(flightCode) ?: return@post call.respond(HttpStatusCode.NotFound, "Flight not found")
            val flightId = flight.id
            val returnFlightId = if(returnFlightCode != null) {
                val returnFlight = flightRepository.getFlightByFlightCode(returnFlightCode) ?: return@post call.respond(HttpStatusCode.NotFound, "Return Flight not found")
                returnFlight.id
            }
            else {
                null
            }
            val booking = bookingRepository.createBooking(session.userId, flightId, returnFlightId)
            
            call.respondRedirect("/booking/${booking.bookingReference}/passengers")
        }
        
        get("/{reference}/passengers") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")

            if (booking.userId != session.userId) {
                return@get call.respond(HttpStatusCode.Forbidden, "Not your booking")
            }

            val passengers = bookingRepository.getPassengersByBookingId(booking.id)

            call.respondPebble(
                "passengers.peb",
                mapOf(
                    "passengers" to passengers,
                    "reference" to reference ,
                )
            )
        }
        post("/{reference}/passengers") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()

            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@post call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
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

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@get call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
            }

            val seats = bookingRepository.getSeatsByFlightId(booking.flightId)
            if(booking.returnFlightId != null) {
                val returnSeats = bookingRepository.getSeatsByFlightId(booking.returnFlightId)
                return@get call.respondPebble("returnseats.peb", mapOf("seats" to seats, "returnSeats" to returnSeats))
            }
            //call.respond(seats)
            call.respondPebble("seats.peb", mapOf("seats" to seats))
        }

        post("/{reference}/ticket_assignment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")

            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@post call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
            }

            val flight = flightRepository.getFlightByFlightId(booking.flightId) ?: return@post call.respond(HttpStatusCode.NotFound, "Flight not found")

            val returnFlight = if(booking.returnFlightId != null) { 
                flightRepository.getFlightByFlightId(booking.returnFlightId) ?: return@post call.respond(HttpStatusCode.NotFound, "Return Flight not found")
            }
            else {
                null
            }
            
            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            try {
                transaction {
                    //Check if passengers already in ticketassignment table and delete to allow reselected seats to take over
                    bookingRepository.deleteOldSeatSelectionsByBookingReference(reference)

                    for(passenger in passengers) {
                        val flightSeatId = params["passenger_${passenger.id}_seat_id"]?.toIntOrNull() ?: throw BadRequestException("Missing flight seat id for passenger ${passenger.id}")
                        val seatClass = bookingRepository.getSeatClassByFlightSeatId(flightSeatId) ?:  throw NotFoundException("Outbound Seat Class not found")
                        val seatNumber = bookingRepository.getSeatNumberByFlightSeatId(flightSeatId) ?: throw NotFoundException("Outbound Seat Number not found")
                        val date = flight.departureTime.toLocalDate()
                        val ticketPrice = bookingRepository.calculatePrice(flight.minPrice, seatClass, date)

                        try {
                            bookingRepository.ticketAssignment(passenger.id, flightSeatId, ticketPrice, seatNumber)
                        }
                        catch(e: Exception) {
                            throw Exception ("Seat already taken") // Shouldn't be possible to select
                        }
                        if(returnFlight != null) {
                            val returnFlightSeatId = params["passenger_${passenger.id}_return_seat_id"]?.toIntOrNull() ?: throw BadRequestException("Missing return flight seat id for passenger ${passenger.id}")
                            val returnSeatClass = bookingRepository.getSeatClassByFlightSeatId(returnFlightSeatId) ?:  throw NotFoundException("Return Seat Class not found")
                            val returnSeatNumber = bookingRepository.getSeatNumberByFlightSeatId(returnFlightSeatId) ?: throw NotFoundException("Return Seat Number not found")
                            val returnDate = returnFlight.departureTime.toLocalDate()
                            val returnTicketPrice = bookingRepository.calculatePrice(returnFlight.minPrice, returnSeatClass, returnDate)
                            

                            try {
                                bookingRepository.ticketAssignment(passenger.id, returnFlightSeatId, returnTicketPrice, returnSeatNumber)
                            }
                            catch(e: Exception) {
                                throw Exception ("Seat already taken") // Shouldn't be possible to select
                            }
                        }
                    }
                }
                
                call.respondRedirect("/booking/$reference/payment")

            } 
            catch (e: NotFoundException) {
                call.respondPebble("seats.peb", "Information not found")
            } 
            catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Provided information missing")
            } 
            catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, "Seat already taken")
            }
        }

        get("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if(booking.userId != session.userId) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@get call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
            }

            val price = bookingRepository.getBookingPricePriceByBookingId(booking.id)
            if( price == BigDecimal.ZERO) { return@get call.respond(HttpStatusCode.InternalServerError, "Booking price zero, mistake made") }
            //call.respond(price)
            call.respondPebble("payment.peb", mapOf("price" to price, "reference" to reference))
        }

        post("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@post call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
            }

            val amount = bookingRepository.getBookingPricePriceByBookingId(booking.id)
            if( amount == BigDecimal.ZERO) { return@post call.respond(HttpStatusCode.InternalServerError, "Booking amount zero, mistake made") }
            val paymentMethodParam = params["payment_method"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing payment method")
            val paymentMethod = PaymentMethod.valueOf(paymentMethodParam)
            val payment = bookingRepository.createPayment(booking.id, amount, paymentMethod)

            val confirmed = bookingRepository.confirmBooking(booking)
            if(confirmed != 1) { return@post  call.respond(HttpStatusCode.InternalServerError, "Couldn't confirm booking")} 

            call.respond(payment)
            //call.respondPebble("paymentConfirmation.peb", mapOf("payment" to payment))
        }

        post("/{reference}/send_tickets") { //button on paymentConfirmation.peb
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respond(HttpStatusCode.NotFound, "Logged in user not found")
            
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            val passengers = bookingRepository.getPassengersByBookingId(booking.id)

            val outboundTickets = passengers.map { bookingRepository.getTicketInfoByPassengerAndBooking(it, booking) }
            val allTickets = if (booking.returnFlightId != null) {
                outboundTickets + passengers.map { bookingRepository.getReturnTicketInfoByPassengerAndBooking(it, booking) }
            } else {
                outboundTickets
            }

            for(ticket in allTickets) {
                userRepository.sendEmail(
                    email = user.email,
                    subject = "Your Aero-Flow Ticket — ${ticket.bookingReference}",
                    body = """
                        Passenger: ${ticket.passengerName}
                        From: ${ticket.departureAirportNameCode}
                        To: ${ticket.arrivalAirportNameCode}
                        Departure: ${ticket.dateTime}
                        Seat: ${ticket.seatNumber}
                        Booking Reference: ${ticket.bookingReference}
                    """.trimIndent()
                )
            }
            call.respond(allTickets)
            //call.respondPebble("tickets.peb", mapOf("tickets" to allTickets))
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

        val bookingsInfo = bookings.map { bookingRepository.getBookingInfoByBooking(it) }

        call.respond(bookingsInfo)
        //call.respondPebble("reviewbookings.peb", mapOf("bookingsInfo" to bookingsInfo))
    }
}