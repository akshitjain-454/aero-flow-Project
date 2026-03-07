package com.FlightBooking

import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule


import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

import com.flightbooking.database.DatabaseFactory
import com.flightbooking.routes.userRoutes
import com.flightbooking.routes.flightRoutes
import com.flightbooking.routes.userInteractionRoutes

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
    jackson {
        enable(SerializationFeature.INDENT_OUTPUT)
        registerModule(JavaTimeModule()) // <-- important for LocalDateTime
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

    routing{
        get("/"){
            call.respondText("Flight Booking Running")
        }
        userRoutes()
        flightRoutes()
        userInteractionRoutes()
    }
}
