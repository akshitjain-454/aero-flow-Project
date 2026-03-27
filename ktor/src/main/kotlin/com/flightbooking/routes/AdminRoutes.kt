package com.flightbooking.routes

import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.repositories.AdminRepository
import com.flightbooking.sessions.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.LocalDateTime
import java.time.LocalDate

fun Route.adminRoutes() {

    val complaintRepository = ComplaintRepository()
    val adminRepository = AdminRepository()

    route("/admin") {

        get {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admins only")
                )
            }
            
            // THE FIX: Change this to respondPebble
            call.respondPebble("management.peb", mapOf(
                "isLoggedIn" to true,
                "userInitials" to session.initials 
            ))
        }
        get {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                // Redirect normal users back to the homepage instead of showing a JSON error
                return@get call.respondRedirect("/") 
            }
            
            // Serve the new Management Dashboard!
            call.respondPebble("management.peb", mapOf(
                "isLoggedIn" to true,
                "userInitials" to session.initials // Passes the initials for the top right profile button
            ))
        }

        get("/complaints") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val complaints = complaintRepository.getAllComplaints()
            call.respond(complaints)
        }

        get("/flights/availability") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }
            
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val report = adminRepository.getFlightAvailabilityReport(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "flight_availability",
                    "totalFlightsInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/bookings/cancelled") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }
            val cancelledBookings = adminRepository.getCancelledBookings(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "cancelled_bookings",
                    "totalCancelledBookings" to cancelledBookings.size,
                    "results" to cancelledBookings
                )
            )
        }

        get("/flights/cancelled") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
        
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val cancelledFlights = adminRepository.getCancelledFlights(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "cancelled_flights",
                    "totalCancelledFlights" to cancelledFlights.size,
                    "results" to cancelledFlights
                )
            )
        }

        post("/complaints/{id}/status") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@post call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }

            val complaintIdText = call.parameters["id"]
            val complaintId = complaintIdText?.toIntOrNull()

            if (complaintId == null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid complaint id")
                )
            }

            val params = call.receiveParameters()
            val statusText = params["status"]?.trim()?.uppercase()

            if (statusText.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing status")
                )
            }

            val newStatus = try {
                ComplaintStatus.valueOf(statusText)
            } catch (error: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Invalid status",
                        "allowedStatuses" to ComplaintStatus.values().map { it.name }
                    )
                )
            }

            val complaint = complaintRepository.getComplaintById(complaintId)

            if (complaint == null) {
                return@post call.respond(HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found")
                )
            }

            val updatedComplaint = complaintRepository.updateComplaintStatus(complaintId, newStatus)

            if (updatedComplaint == null) {
                return@post call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update complaint status")
                )
            }
            call.respond(HttpStatusCode.OK,
                mapOf(
                    "message" to "Complaint status updated successfully",
                    "complaintId" to updatedComplaint.id,
                    "oldStatus" to complaint.status.name,
                    "newStatus" to updatedComplaint.status.name
                )
            )
        }

        get("/reports/bookings-per-flight") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }

            val report = adminRepository.getBookingsPerFlightReport()

            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight",
                    "totalFlightsInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/reports/bookings-per-flight/{flightCode}") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }

            val flightCode = call.parameters["flightCode"]?.trim()
            
            if (flightCode.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest,
                mapOf("error" to "Missing flightCode")
                )
            }

            val report = adminRepository.getBookingsPerFlightByFlightCode(flightCode)
                ?: return@get call.respond(HttpStatusCode.NotFound,
                mapOf("error" to "Flight not found")
                )
                
            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight_single",
                    "result" to report
                )
            )
        }

        get("/reports/most-popular-routes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val report = adminRepository.getMostPopularRoutesReport()
            call.respond(
                mapOf(
                    "reportType" to "most_popular_routes",
                    "totalRoutesInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/reports/peak-booking-times") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val report = adminRepository.getPeakBookingTimesReport()
            call.respond(
                mapOf(
                    "reportType" to "peak_booking_times",
                    "totalTimeSlotsInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/flights/changes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }
            val changes = adminRepository.getAllFlightChanges(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "flight_changes",
                    "totalChanges" to changes.size,
                    "results" to changes
                )
            )
        }

        get("/flights/{id}/changes") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }
            val flightId = call.parameters["id"]?.toIntOrNull()?: return@get call.respond(HttpStatusCode.BadRequest,
            mapOf("error" to "Invalid flight id")
            )
            val changes = adminRepository.getFlightChangesByFlightId(flightId)
            call.respond(
                mapOf(
                    "reportType" to "flight_changes_single",
                    "flightId" to flightId,
                    "totalChanges" to changes.size,
                    "results" to changes
                )
            )
        }

        post("/flights/{id}/update-schedule") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            
            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }

            val flightId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Invalid flight id"))
            val params = call.receiveParameters()
            val newDepartureAirportId = params["departureAirportId"]?.toIntOrNull()?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing departureAirportId"))
            val newArrivalAirportId = params["arrivalAirportId"]?.toIntOrNull()?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing arrivalAirportId"))
            val newDepartureTime = params["departureTime"]?.let {
                runCatching { LocalDateTime.parse(it) }.getOrNull()} ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid departureTime"))
            val newArrivalTime = params["arrivalTime"]?.let {
                runCatching { LocalDateTime.parse(it) }.getOrNull()} ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid arrivalTime"))
            val updatedFlight = adminRepository.updateFlightSchedule(
                flightId = flightId,
                newDepartureAirportId = newDepartureAirportId,
                newArrivalAirportId = newArrivalAirportId,
                newDepartureTime = newDepartureTime,
                newArrivalTime = newArrivalTime
            ) ?: return@post call.respond(HttpStatusCode.NotFound,
                mapOf("error" to "Flight not found")
            )
            call.respond(HttpStatusCode.OK,
                mapOf(
                    "message" to "Flight schedule updated successfully",
                    "flightId" to updatedFlight.id,
                    "flightCode" to updatedFlight.flightCode
                )
            )
        }
    }
}