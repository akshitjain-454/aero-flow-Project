package com.flightbooking.routes

import com.flightbooking.repositories.BookingRepository
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.repositories.FlightChangeRequestRepository
import com.flightbooking.repositories.NotificationRepository
import com.flightbooking.repositories.PassengerInfoChangeRequestRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userInteractionRoutes() {

    val complaintRepository = ComplaintRepository()
    val bookingRepository = BookingRepository()
    val flightChangeRequestRepository = FlightChangeRequestRepository()
    val passengerInfoChangeRequestRepository = PassengerInfoChangeRequestRepository()
    val notificationRepository = NotificationRepository()

    post("/complaints") {
        val params = call.receiveParameters()

        val userIdText = params["userId"]
        val message = params["message"]

        if (userIdText == null || message == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing userId or message")
            return@post
        }

        if (message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Message cannot be empty")
            return@post
        }

        val userId = userIdText.toInt()

        complaintRepository.createComplaint(userId, message)
        notificationRepository.createNotification(
            userId,
            "Your complaint has been submitted successfully."
        )

        call.respond(HttpStatusCode.Created, "Complaint submitted successfully")
    }

    post("/bookings/cancel") {
        val params = call.receiveParameters()

        val bookingIdText = params["bookingId"]

        if (bookingIdText == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing bookingId")
            return@post
        }

        val bookingId = bookingIdText.toInt()

        if (!bookingRepository.bookingExists(bookingId)) {
            call.respond(HttpStatusCode.NotFound, "Booking not found")
            return@post
        }

        val booking = bookingRepository.getBookingById(bookingId)

        if (booking.status == "CANCELLED") {
            call.respond(HttpStatusCode.BadRequest, "Booking is already cancelled")
            return@post
        }

        bookingRepository.cancelBooking(bookingId)

        notificationRepository.createNotification(
            booking.userId,
            "Your booking ${booking.bookingReference} has been cancelled."
        )

        call.respond(HttpStatusCode.OK, "Booking cancelled successfully")
    }

    post("/flight_change_requests") {
        val params = call.receiveParameters()

        val bookingIdText = params["bookingId"]
        val requestFlightIdText = params["requestFlightId"]

        if (bookingIdText == null || requestFlightIdText == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing bookingId or requestFlightId")
            return@post
        }

        val bookingId = bookingIdText.toInt()
        val requestFlightId = requestFlightIdText.toInt()

        if (!bookingRepository.bookingExists(bookingId)) {
            call.respond(HttpStatusCode.NotFound, "Booking not found")
            return@post
        }

        val booking = bookingRepository.getBookingById(bookingId)

        flightChangeRequestRepository.createFlightChangeRequest(bookingId, requestFlightId)

        notificationRepository.createNotification(
            booking.userId,
            "Your flight change request has been submitted."
        )

        call.respond(HttpStatusCode.Created, "Flight change request submitted successfully")
    }

    post("/passenger_info_change_requests") {
        val params = call.receiveParameters()

        val bookingIdText = params["bookingId"]
        val newFirstname = params["newFirstname"]
        val newLastname = params["newLastname"]
        val newPassportCode = params["newPassportCode"]

        if (bookingIdText == null || newFirstname == null || newLastname == null || newPassportCode == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required fields")
            return@post
        }

        val bookingId = bookingIdText.toInt()

        if (!bookingRepository.bookingExists(bookingId)) {
            call.respond(HttpStatusCode.NotFound, "Booking not found")
            return@post
        }

        val booking = bookingRepository.getBookingById(bookingId)

        passengerInfoChangeRequestRepository.createPassengerInfoChangeRequest(
            bookingId,
            newFirstname,
            newLastname,
            newPassportCode
        )

        notificationRepository.createNotification(
            booking.userId,
            "Your passenger information change request has been submitted."
        )

        call.respond(HttpStatusCode.Created, "Passenger information change request submitted successfully")
    }

    get("/users/{userId}/notifications") {
        val userIdText = call.parameters["userId"]

        if (userIdText == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing userId")
            return@get
        }

        val userId = userIdText.toInt()
        val notifications = notificationRepository.getNotificationsByUserId(userId)

        call.respond(HttpStatusCode.OK, notifications)
    }

    post("/notifications/read") {
        val params = call.receiveParameters()

        val notificationIdText = params["notificationId"]

        if (notificationIdText == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing notificationId")
            return@post
        }

        val notificationId = notificationIdText.toInt()
        val updated = notificationRepository.markNotificationAsRead(notificationId)

        if (!updated) {
            call.respond(HttpStatusCode.NotFound, "Notification not found")
            return@post
        }

        call.respond(HttpStatusCode.OK, "Notification marked as read")
    }
}
