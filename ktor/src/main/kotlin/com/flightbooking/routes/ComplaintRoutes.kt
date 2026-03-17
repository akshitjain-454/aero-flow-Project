package com.flightbooking.routes

import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.sessions.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

// ---> ADD THIS IMPORT FOR PEBBLE <---
import io.ktor.server.pebble.PebbleContent

fun Route.complaintRoutes() {

    val complaintRepository = ComplaintRepository()

    route("/complaints") {
        post("/submit") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val params = call.receiveParameters()
            val message = params["message"]?.trim()

            if (message.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Complaint message is required")
            }

            val complaint = complaintRepository.createComplaint(userId = session.userId, message = message)

            call.respond(
                HttpStatusCode.Created,
                mapOf(
                    "message" to "Complaint submitted successfully",
                    "complaintId" to complaint.id,
                    "status" to complaint.status.toString(),
                    "createdAt" to complaint.createdAt.toString()
                )
            )
        }
        
        // ---> THIS IS THE ONLY PART YOU CHANGE <---
        get("/my") {
            //val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val fakeUserId = 1
            // 1. Get the complaints from your database
            val complaints = complaintRepository.getComplaintsByUserId(fakeUserId)
            
            // 2. Pass them into the Pebble template!
            call.respond(
                PebbleContent(
                    "complaints.peb", 
                    mapOf("complaints" to complaints)
                )
            )
        }
    }
}