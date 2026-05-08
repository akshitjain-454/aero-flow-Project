package com.flightbooking

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.flightbooking.database.DatabaseFactory
import com.flightbooking.enums.UserRole
import com.flightbooking.routes.adminRoutes
import com.flightbooking.routes.bookingRoutes
import com.flightbooking.routes.complaintRoutes
import com.flightbooking.routes.flightRoutes
import com.flightbooking.routes.notificationRoutes
import com.flightbooking.routes.userRoutes
import com.flightbooking.sessions.UserSession
import com.flightbooking.sessions.VerificationSession
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.maxAge
import io.ktor.server.sessions.sessions
import io.ktor.util.hex
import io.pebbletemplates.pebble.loader.ClasspathLoader
import kotlin.time.Duration.Companion.minutes

private const val VERIFICATION_SESSION_MAX_AGE_MINUTES = 15

suspend fun ApplicationCall.respondPebble(
    template: String,
    model: Map<String, Any> = emptyMap(),
) {
    val session = sessions.get<UserSession>()
    respond(
        PebbleContent(
            template,
            model +
                mapOf(
                    "userId" to (session?.userId ?: -1),
                    "isAdmin" to (session?.role == UserRole.ADMIN),
                    "isLoggedIn" to (session != null),
                    "userInitials" to (session?.initials ?: ""),
                ),
        ),
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
        loader(
            ClasspathLoader().apply {
                prefix = "templates"
            },
        )
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
            cookie.maxAge = VERIFICATION_SESSION_MAX_AGE_MINUTES.minutes
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
        get("/comingsoon") {
            call.respondPebble("comingsoon.peb")
        }
        get("destinations") {
            call.respondPebble("destinations.peb")
        }
        get("/contact") {
            call.respondPebble("Contact.peb")
        }
        post("/submit-contact") {
            call.respondPebble("submitcontact.peb")
        }
        complaintRoutes()
        userRoutes()
        flightRoutes()
        bookingRoutes()
        adminRoutes()
        notificationRoutes()
    }
}
