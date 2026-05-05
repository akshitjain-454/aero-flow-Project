package com.flightbooking.routes
import com.flightbooking.services.NotificationService
import com.flightbooking.services.NotificationEvent
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
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect

fun Route.notificationRoutes() {
    
    route("/notifications") {
        get("/stream") {
            val session = call.sessions.get<UserSession>() ?: return@get

            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                NotificationService.events
                .filter{it.userId ==  session.userId}
                .collect { event ->
                    write("data: {\"message\":\"${event.message}\",\"type\":\"${event.type}\"}\n\n")
                    flush()
                }
            }
        }
    }
}