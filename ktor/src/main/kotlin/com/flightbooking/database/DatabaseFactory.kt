package com.flightbooking.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import com.flightbooking.tables.*
//Only for testing
import com.flightbooking.database.seed.DefaultDataSeeder

object DatabaseFactory {

    fun init() {
        Database.connect(
            url = "jdbc:sqlite:data/flight_app.db",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(
                AirportTable,
                AircraftTable,
                FlightTable,
                SeatTable,
                FlightSeatTable,
                UserTable,
                PassengerTable,
                BookingTable,
                TicketAssignmentTable,
                PaymentTable,
                ComplaintTable,
                FlightChangeLogTable
            )
            //Only for testing
            if (UserTable.selectAll().none()) {
                DefaultDataSeeder.seed()
            }
            if (SeatTable.selectAll().any() && FlightTable.selectAll().any()) {
                DefaultDataSeeder.seedFlightSeats()
            }
        }
    }
}