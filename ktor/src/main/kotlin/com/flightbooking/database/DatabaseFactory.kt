package com.flightbooking.database

import com.flightbooking.database.seed.DefaultDataSeeder
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.FlightChangeLogTable
import com.flightbooking.tables.FlightInfoRequestTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:sqlite:data/flight_app.db",
            driver = "org.sqlite.JDBC",
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
                FlightChangeLogTable,
                FlightInfoRequestTable,
            )
            // Only for testing
            if (UserTable.selectAll().none()) {
                DefaultDataSeeder.seed()
            }
            // Needed
            if (SeatTable.selectAll().any() && FlightTable.selectAll().any()) {
                DefaultDataSeeder.seedFlightSeats()
            }
        }
    }
}
