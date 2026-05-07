package com.flightbooking.routes

import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

fun Route.notificationRoutes() {
    route("/notifications") {
        get("/stream") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, "Not Found")
                return@get
            }

            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.headers.append(HttpHeaders.TransferEncoding, "chunked")

            try {
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    NotificationService.events
                        .filter { it.userId == session.userId }
                        .collect { event ->
                            writeStringUtf8(
                                "data: {\"message\":\"${event.message}\",\"type\":\"${event.type}\",\"sentAt\":${event.sentAt}}\n\n",
                            )
                            flush()
                        }
                }
            } catch (_: Exception) {
            }
        }
    }
}
