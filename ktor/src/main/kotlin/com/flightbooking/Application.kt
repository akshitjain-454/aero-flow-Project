package com.FlightBooking

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.sessions.*
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.util.hex
import com.flightbooking.database.DatabaseFactory
import com.flightbooking.routes.userRoutes
import com.flightbooking.routes.flightRoutes
import com.flightbooking.routes.bookingRoutes
import com.flightbooking.sessions.UserSession
import io.pebbletemplates.ktor.Pebble
import io.pebbletemplates.ktor.PebbleContent
import io.pebbletemplates.ktor.loader.ClasspathLoader

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Init database
    DatabaseFactory.init()

    // Content negotiation
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    // Sessions (optional)
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
            val encryptKey = hex("ef82ffacc3920ae250206ead14bfcfff")
            val signKey = hex("ab18cf1251005ede247e911a1e72ab67")
            transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
        }
    }

    // Pebble templating
    install(Pebble) {
        loader = ClasspathLoader().apply {
            prefix = "templates"
            suffix = ".peb"
        }
        cacheActive = false
    }

    // Routing
    routing {
        get("/") {
            call.respond(PebbleContent("index.peb", mapOf<String, Any>()))
        }
        get("/login") {
            call.respond(PebbleContent("login.peb", mapOf<String, Any>()))
        }

        userRoutes()
        flightRoutes()
        bookingRoutes()
    }
}