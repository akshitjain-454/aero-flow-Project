package com.FlightBooking

import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.hex

import com.flightbooking.sessions.UserSession
import com.flightbooking.database.DatabaseFactory
import com.flightbooking.routes.userRoutes

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

    install(Sessions){
        cookie<UserSession>("user_session"){
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
        }
        
        val encryptKey = hex("ef82ffacc3920ae250206ead14bfcfff")
        val signKey = hex("ab18cf1251005ede247e911a1e72ab67")
        transform(
            SessionTransportTransformerEncrypt(encryptKey, signKey)
        )
    }
}

    routing{
        get("/"){
            call.respondText("Flight Booking Running")
        }
        userRoutes()
    }
}
