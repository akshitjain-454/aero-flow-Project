package com.flightbooking.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import com.flightbooking.tables.*

object DatabaseFactory {

    fun init() {
        Database.connect(
            url = "jdbc:sqlite:data/flight_app.db",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(
                UserTable, 
                BookingTable, 
                FlightTable, 
                AircraftTable, 
                AirportTable, 
                PassengerTable, 
                PaymentTable, 
                ComplaintTable, 
                SeatTable, 
                TicketAssignmentTable, 
                FlightSeatTable, 
            )
        }
    }
}