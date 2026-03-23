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
            call.respond(
                mapOf(
                    "message" to "Welcome to admin page",
                    "role" to session.role.name,
                    "userId" to session.userId
                )
            )
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

    }
}
