package com.flightbooking.routes

import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.respondPebble
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.pebble.PebbleContent

fun Route.complaintRoutes() {

    val complaintRepository = ComplaintRepository()

    

    route("/complaints") {

        // GET /complaints — renders the complaints page (linked from Help dropdown)
        get() {
            val session = call.sessions.get<UserSession>()
            val complaints = if (session != null) {
                complaintRepository.getComplaintsByUserId(session.userId)
            } else {
                emptyList()
            }
            call.respondPebble("complaints.peb", mapOf("complaints" to complaints))
        }

        // POST /complaints/submit — submits a new complaint
        post("/submit") {
            val session = call.sessions.get<UserSession>() 
                ?: return@post call.respondRedirect("/login")
            
            val params = call.receiveParameters()
            val message = params["message"]?.trim()

            if (message.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Complaint message is required")
            }

            complaintRepository.createComplaint(userId = session.userId, message = message)
            
            // Redirect back to complaints page after submitting
            call.respondRedirect("/complaints")
        }

        // GET /complaints/my — same page but session-gated
        get("/my") { //unused in frontend
            val session = call.sessions.get<UserSession>() 
                ?: return@get call.respondRedirect("/login")
            
            val complaints = complaintRepository.getComplaintsByUserId(session.userId)
            call.respondPebble("complaints.peb", mapOf("complaints" to complaints))
        }
    }
}