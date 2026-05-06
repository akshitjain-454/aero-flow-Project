package com.flightbooking.routes

import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

fun Route.notificationRoutes() {
    route("/notifications") {
        get("/stream") {
            val session = call.sessions.get<UserSession>() ?: return@get

            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                NotificationService.events
                    .filter { it.userId == session.userId }
                    .collect { event ->
                        write("data: {\"message\":\"${event.message}\",\"type\":\"${event.type}\",\"sentAt\":${event.sentAt}}\n\n")
                        flush()
                    }
            }
        }
    }
}
