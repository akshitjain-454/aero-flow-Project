package com.FlightBooking

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

import com.flightbooking.database.DatabaseFactory

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()

    routing{
        get("/"){
            call.respondText("Flight Booking Running")
        }
    }
}
