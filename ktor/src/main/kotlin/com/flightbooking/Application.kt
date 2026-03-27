package com.flightbooking

import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.flightbooking.module

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.util.hex

// --- PEBBLE IMPORTS ---
import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader

import com.flightbooking.sessions.*
import com.flightbooking.enums.UserRole
import com.flightbooking.database.DatabaseFactory
import com.flightbooking.repositories.UserRepository
import com.flightbooking.routes.userRoutes
import com.flightbooking.routes.flightRoutes
import com.flightbooking.routes.bookingRoutes
import com.flightbooking.routes.complaintRoutes
import com.flightbooking.routes.adminRoutes
import kotlin.time.Duration.Companion.minutes

suspend fun ApplicationCall.respondPebble(template: String, model: Map<String, Any> = emptyMap()) {
    val session = sessions.get<UserSession>()
    respond(
        PebbleContent(
            template,
            model + mapOf(
                "userId" to (session?.userId ?: -1),
                "isAdmin" to (session?.role == UserRole.ADMIN),
                "isLoggedIn"   to (session != null),
                "userInitials" to (session?.initials ?: "")
            )
        )
    )
}

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
    }

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
            val encryptKey = hex("ef82ffacc3920ae250206ead14bfcfff")
            val signKey = hex("ab18cf1251005ede247e911a1e72ab67")
            
            transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
        }
        cookie<VerificationSession>("verification_session") {
            cookie.path = "/register"
            cookie.maxAge = 15.minutes
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
            val encryptKey = hex("ab20f82cfea398ffffac69ae2d14bf50")
            val signKey = hex("fde2eb1e7e911a29cf151a22ab905f57")
            
            transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
        }
    }

    routing {
        get("/") {
            call.respondPebble("index.peb")
        }
        complaintRoutes()
        userRoutes()
        flightRoutes()
        bookingRoutes()
        adminRoutes()
    }
}