package com.flightbooking.routes

import com.flightbooking.services.NotificationService
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.collect

fun Route.notificationRoutes() {
    route("/notifications") {
        get("/stream") {
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                NotificationService.events.collect { event ->
                    write("data: {\"message\":\"${event.message}\",\"type\":\"${event.type}\"}\n\n")
                    flush()
                }
            }
        }
    }
}
