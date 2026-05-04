package com.flightbooking.routes 

import com.flightbooking.repositories.BookingRepository
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.repositories.UserRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.respondPebble
import io.ktor.server.sessions.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
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
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Already paid") 
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
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Already paid") 
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
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Already paid") 
            }

            val seats = bookingRepository.getSeatsByFlightId(booking.flightId)
            val passengers = bookingRepository.getPassengersByBookingId(booking.id)
            val selectedSeats = bookingRepository.getSelectedSeatsByFlightIdAndPassengers(booking.flightId, passengers)
            if(booking.returnFlightId != null) {
                val returnSeats = bookingRepository.getSeatsByFlightId(booking.returnFlightId)
                val selectedReturnSeats = bookingRepository.getSelectedSeatsByFlightIdAndPassengers(booking.returnFlightId, passengers)
                return@get call.respondPebble("returnseats.peb", mapOf(
                    "seats" to seats, 
                    "returnSeats" to returnSeats,
                    "passengers" to passengers,
                    "reference" to reference,
                    "selectedSeats" to selectedSeats, 
                    "selectedReturnSeats" to selectedReturnSeats
                    )
                )
            }
            //call.respond(seats)
            call.respondPebble("seats.peb", mapOf(
                "seats" to seats,
                "passengers" to passengers,
                "reference" to reference,
                "selectedSeats" to selectedSeats
                )
            )
        }

        post("/{reference}/ticket_assignment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")

            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Already paid") 
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
                        val flightSeatId = params["passenger_${passenger.id}_seat_id"]?.toIntOrNull() ?: throw Exception("Missing flight seat id for passenger ${passenger.id}")
                        val seatClass = bookingRepository.getSeatClassByFlightSeatId(flightSeatId) ?:  throw Exception("Outbound Seat Class not found")
                        val seatNumber = bookingRepository.getSeatNumberByFlightSeatId(flightSeatId) ?: throw Exception("Outbound Seat Number not found")
                        val date = flight.departureTime.toLocalDate()
                        val ticketPrice = bookingRepository.calculatePrice(flight.minPrice, seatClass, date)

                        try {
                            bookingRepository.ticketAssignment(passenger.id, flightSeatId, ticketPrice, seatNumber)
                        }
                        catch(e: Exception) {
                            throw Exception ("Seat already taken") // Shouldn't be possible to select
                        }
                        if(returnFlight != null) {
                            val returnFlightSeatId = params["passenger_${passenger.id}_return_seat_id"]?.toIntOrNull() ?: throw Exception("Missing return flight seat id for passenger ${passenger.id}")
                            val returnSeatClass = bookingRepository.getSeatClassByFlightSeatId(returnFlightSeatId) ?:  throw Exception("Return Seat Class not found")
                            val returnSeatNumber = bookingRepository.getSeatNumberByFlightSeatId(returnFlightSeatId) ?: throw Exception("Return Seat Number not found")
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
            catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Something Went Wrong")
            }
        }

        get("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if(booking.userId != session.userId) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Already paid") 
            }

            val price = bookingRepository.getBookingPricePriceByBookingId(booking.id)
            if( price == BigDecimal.ZERO) { return@get call.respond(HttpStatusCode.InternalServerError, "Booking price zero, mistake made") }
            //call.respond(price)
            val loyaltyPoints = bookingRepository.getLoyaltyPointsByUserId(booking.userId)
            call.respondPebble("payment.peb", mapOf("price" to price, "loyaltyPoints" to loyaltyPoints, "reference" to reference))
        }

        post("/{reference}/payment") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val params = call.receiveParameters()
            val booking = bookingRepository.getBookingByReference(reference) ?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            if(booking.userId != session.userId) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }
            if(booking.status == BookingStatus.CONFIRMED) { 
                return@post call.respond(HttpStatusCode.Forbidden, "Already paid") 
            }

            //Stops booking if taking too long
            if (booking.createdAt.plusMinutes(30) < LocalDateTime.now()) {
                bookingRepository.deleteBookingByReference(reference)
                return@post call.respondPebble("index.peb", mapOf("error" to "Your booking session expired. Please search again."))
            }

            val amount = bookingRepository.getBookingPricePriceByBookingId(booking.id)
            if( amount == BigDecimal.ZERO) { return@post call.respond(HttpStatusCode.InternalServerError, "Booking amount zero, mistake made") }
            val paymentMethodParam = params["payment_method"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing payment method")
            val useLoyaltyPoints = (params["use_loyalty_points"] == "on")
            println("use_loyalty_points param: ${params["use_loyalty_points"]}")
            val paymentMethod = PaymentMethod.valueOf(paymentMethodParam)

            val payment = if(useLoyaltyPoints) {
                val discountedAmount = bookingRepository.useUsersLoyaltyPoints(booking.userId, amount)
                bookingRepository.createPayment(booking.id, discountedAmount, paymentMethod)
            }
            else {
                bookingRepository.createPayment(booking.id, amount, paymentMethod)
            }

            val addedPoints = bookingRepository.addLoyaltyPointsByUserIdAndBookingAmount(booking.userId, amount)

            val confirmed = bookingRepository.confirmBooking(booking)
            if(confirmed != 1) { return@post  call.respond(HttpStatusCode.InternalServerError, "Couldn't confirm booking")} 

            //call.respond(payment)
            call.respondPebble("paymentConfirmation.peb", mapOf("payment" to payment, "addedPoints" to addedPoints, "reference" to reference))
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
                        Class: ${ticket.seatClass}
                        Booking Reference: ${ticket.bookingReference}
                    """.trimIndent()
                )
            }
            call.respond(allTickets)
        }

        // --- VIEW TICKETS ---
        get("/{reference}/tickets") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if(booking.userId != session.userId) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            val passengers = bookingRepository.getPassengersByBookingId(booking.id)

            val outboundTickets = passengers.map { bookingRepository.getTicketInfoByPassengerAndBooking(it, booking) }
            val allTickets = if (booking.returnFlightId != null) {
                outboundTickets + passengers.map { bookingRepository.getReturnTicketInfoByPassengerAndBooking(it, booking) }
            } else {
                outboundTickets
            }

            // This loads the visual of boarding passes!
            call.respondPebble("tickets.peb", mapOf("tickets" to allTickets))
        }

        // --- VIEW THE FLIGHT MAP ---
        get("/{reference}/map") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val reference = call.parameters["reference"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            
            val booking = bookingRepository.getBookingByReference(reference) ?: return@get call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if(booking.userId != session.userId) { 
                return@get call.respond(HttpStatusCode.Forbidden, "Not the users booking") 
            }

            // Fetch the full booking info to get the departure and arrival airport names
            val bookingInfo = bookingRepository.getBookingInfoByBooking(booking)

            call.respondPebble("flightmap.peb", mapOf("booking" to bookingInfo))
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

        post("/{reference}/clear") {
            val session = call.sessions.get<UserSession>()?: return@post call.respondRedirect("/login")
            val reference = call.parameters["reference"]?: return@post call.respond(HttpStatusCode.BadRequest, "Missing booking reference")
            val booking = bookingRepository.getBookingByReference(reference)?: return@post call.respond(HttpStatusCode.NotFound, "Booking not found")
            
            if (booking.userId != session.userId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Not the user's booking")
            }
            
            val cancelledBooking = bookingRepository.deleteBookingByReference(reference)
            
            val bookings = bookingRepository.getBookingsByUserId(session.userId)
            val bookingsInfo = bookings.map { bookingRepository.getBookingInfoByBooking(it) }

            call.respondPebble("reviewbookings.peb", mapOf("bookingsInfo" to bookingsInfo))
        }

    } // END of route("/booking")
    
    //flight information change route
    route("/flight-info-requests") {
        get {
            val session = call.sessions.get<UserSession>()?: return@get call.respondRedirect("/login")
            val bookings = bookingRepository.getBookingsByUserId(session.userId)
            val bookingOptions = bookings.map { booking ->
                mapOf(
                    "booking" to booking,
                    "info" to bookingRepository.getBookingInfoByBooking(booking),
                    "passengers" to bookingRepository.getPassengersByBookingId(booking.id)
                )
            }
            val requests = bookingRepository.getFlightInfoRequestsByUserId(session.userId)
            val selectedReference = call.request.queryParameters["bookingReference"] ?: ""

            call.respondPebble(
                "flight-info-requests.peb",
                mapOf(
                    "bookingOptions" to bookingOptions,
                    "requests" to requests,
                    "selectedReference" to selectedReference
                )
            )
        }

        post("/submit") {
            val session = call.sessions.get<UserSession>()?: return@post call.respondRedirect("/login")
            val params = call.receiveParameters()
            val bookingReference = params["bookingReference"]?.trim()?: return@post call.respond(HttpStatusCode.BadRequest, "Booking reference is required")
            
            val requestType = try {
                FlightInfoRequestType.valueOf(params["requestType"] ?: "BOTH")
            } catch (error: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    "Invalid request type"
                )
            }
            val passengerId = params["passengerId"]?.toIntOrNull()
            val newFirstname = params["newFirstname"]?.trim()
            val newLastname = params["newLastname"]?.trim()
            val newPassportCode = params["newPassportCode"]?.trim()
            val requestedFlightCode = params["requestedFlightCode"]?.trim()?.uppercase()
            val message = params["message"]?.trim()

            val needsPassengerInfo = requestType == FlightInfoRequestType.PASSENGER_INFO || requestType == FlightInfoRequestType.BOTH
            val needsFlightChange = requestType == FlightInfoRequestType.FLIGHT_CHANGE || requestType == FlightInfoRequestType.BOTH
            val hasPassengerInfoChange = !newFirstname.isNullOrBlank() || !newLastname.isNullOrBlank() || !newPassportCode.isNullOrBlank()

            if (needsPassengerInfo && passengerId == null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    "Please select a passenger for passenger information changes"
                )
            }

            if (needsPassengerInfo && !hasPassengerInfoChange) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    "Please enter at least one passenger information change"
                )
            }

            if (needsFlightChange && requestedFlightCode.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    "Requested flight code is required"
                )
            }
            val success = bookingRepository.createFlightInfoRequest(
                userId = session.userId,
                bookingReference = bookingReference,
                requestType = requestType,
                passengerId = passengerId,
                newFirstname = newFirstname,
                newLastname = newLastname,
                newPassportCode = newPassportCode,
                requestedFlightCode = requestedFlightCode,
                message = message
            )

            if (!success) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Booking not found, passenger not found, or booking does not belong to you"
                )
            }
            call.respondRedirect("/flight-info-requests")
        }
    }

    get("/review_bookings") {
        val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
        val bookings = bookingRepository.getBookingsByUserId(session.userId)

        val bookingsInfo = bookings.map { bookingRepository.getBookingInfoByBooking(it) }

        //call.respond(bookingsInfo)
        call.respondPebble("reviewbookings.peb", mapOf("bookingsInfo" to bookingsInfo))
    }
    
}