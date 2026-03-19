package com.flightbooking.routes

import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.respondPebble
import com.flightbooking.sessions.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.adminRoutes() {
    val complaintRepository = ComplaintRepository()

    route("/admin") {

        get {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden, "Admin only")
            }
            call.respondPebble("admin/index.peb")
        }

        get("/complaints") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden, "Admin only")
            }
            val complaints = complaintRepository.getAllComplaints()
            call.respondPebble(
                "admin/complaints.peb",
                mapOf("complaints" to complaints)
            )
        }

        post("/complaints/{id}/status") {
            val idText = call.parameters["id"]

            if (idText == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "Missing complaint id")
            }
            
            val complaintId = idText.toIntOrNull()

            if (complaintId == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid complaint id")
            }

            val params = call.receiveParameters()
            val statusText = params["status"]

            if (statusText == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "Missing status")
            }

            val newStatus = try {
                ComplaintStatus.valueOf(statusText)
            } catch (error: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid complaint status")
            }

            val updatedComplaint = complaintRepository.updateComplaintStatus(complaintId, newStatus)
            if (updatedComplaint == null) {
                return@post call.respond(HttpStatusCode.NotFound, "Complaint not found")
            }
            call.respondRedirect("/admin/complaints")
        }
    }
}