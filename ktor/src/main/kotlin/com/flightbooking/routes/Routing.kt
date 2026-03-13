package com.flightbooking.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.ktor.PebbleContent

fun Route.userRoutes() {
    get("/user") {
        call.respond(PebbleContent("user.peb", mapOf<String, Any>()))
    }
}