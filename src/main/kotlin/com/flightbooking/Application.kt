package com.flightbooking

import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.hex

import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader

import com.flightbooking.sessions.*
import com.flightbooking.enums.UserRole
import com.flightbooking.database.DatabaseFactory
import com.flightbooking.routes.*
import kotlin.time.Duration.Companion.minutes

// ─────────────────────────────
// Pebble helper
// ─────────────────────────────
suspend fun ApplicationCall.respondPebble(
    template: String,
    model: Map<String, Any> = emptyMap()
) {
    val session = sessions.get<UserSession>()
    respond(
        PebbleContent(
            template,
            model + mapOf(
                "userId" to (session?.userId ?: -1),
                "isAdmin" to (session?.role == UserRole.ADMIN),
                "isLoggedIn" to (session != null),
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
            transform(
                SessionTransportTransformerEncrypt(
                    hex("ef82ffacc3920ae250206ead14bfcfff"),
                    hex("ab18cf1251005ede247e911a1e72ab67")
                )
            )
        }

        cookie<VerificationSession>("verification_session") {
            cookie.path = "/register"
            cookie.maxAge = 15.minutes
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
            transform(
                SessionTransportTransformerEncrypt(
                    hex("ab20f82cfea398ffffac69ae2d14bf50"),
                    hex("fde2eb1e7e911a29cf151a22ab905f57")
                )
            )
        }
    }

    routing {

        get("/") {
            call.respondPebble("index.peb")
        }

     
        get("/search-page") {
            call.respondPebble("search.peb")
        }

       
        get("/flightsearch") {
            call.respondRedirect("/search")
        }

        
        get("/round-trip") { call.respondPebble("roundtrip.peb") }
        get("/multi-city") { call.respondPebble("multicity.peb") }
        get("/destinations") { call.respondPebble("destinations.peb") }

        get("/review_bookings") { call.respondPebble("reviewbookings.peb") }
        get("/checkin") { call.respondPebble("checkin.peb") }
        get("/seats") { call.respondPebble("seats.peb") }
        get("/passengers") { call.respondPebble("passengers.peb") }

        get("/cabins") { call.respondPebble("cabins.peb") }
        get("/dining") { call.respondPebble("dining.peb") }
        get("/entertainment") { call.respondPebble("index.peb") }
        get("/lounges") { call.respondPebble("index.peb") }

        route("/loyalty") {
            get { call.respondPebble("loyalty_earn.peb") }
            get("/join") { call.respondPebble("loyalty_join.peb") }
            get("/earn") { call.respondPebble("loyalty_earn.peb") }
            get("/redeem") { call.respondPebble("loyalty_redeem.peb") }
            get("/tiers") { call.respondPebble("loyalty_tiers.peb") }
            get("/partners") { call.respondPebble("loyalty_partners.peb") }
        }

        get("/contact") { call.respondPebble("Contact.peb") }
        get("/faqs") { call.respondPebble("index.peb") }
        get("/flight-status") { call.respondPebble("flight-status.peb") }
        get("/travel-alerts") { call.respondPebble("travel-alerts.peb") }

        get("/login") { call.respondPebble("login.peb") }
        get("/profile") { call.respondPebble("profile.peb") }

        
        complaintRoutes()
        userRoutes()
        flightRoutes()
        bookingRoutes()
        adminRoutes()
    }
}