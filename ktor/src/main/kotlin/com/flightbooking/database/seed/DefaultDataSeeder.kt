//Only for testing
package com.flightbooking.database.seed

import com.flightbooking.enums.UserRole
import com.flightbooking.tables.UserTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.FlightSeatTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.batchInsert

object DefaultDataSeeder {

    fun seed() {
        val now = LocalDateTime.now()

        createUserIfMissing(
            firstname = "Normal",
            lastname = "User",
            email = "user@demo.com",
            rawPassword = "123456",
            role = UserRole.USER,
            loyaltyPoints = 0,
            redeemedLoyaltyPoints = 0,
            createdAt = now
        )

        createUserIfMissing(
            firstname = "System",
            lastname = "Admin",
            email = "admin@demo.com",
            rawPassword = "admin123",
            role = UserRole.ADMIN,
            loyaltyPoints = 0,
            redeemedLoyaltyPoints = 0,
            createdAt = now
        )
        seedFlightSeats()
    }

    private fun createUserIfMissing(
        firstname: String,
        lastname: String,
        email: String,
        rawPassword: String,
        role: UserRole,
        loyaltyPoints: Int,
        redeemedLoyaltyPoints: Int,
        createdAt: LocalDateTime
    ) {
        val exists = UserTable
            .selectAll()
            .any { row -> row[UserTable.email] == email }

        if (exists) return

        UserTable.insert {
            it[UserTable.firstname] = firstname
            it[UserTable.lastname] = lastname
            it[UserTable.email] = email
            it[UserTable.passwordHash] = BCrypt.hashpw(rawPassword, BCrypt.gensalt())
            it[UserTable.role] = role
            it[UserTable.loyaltyPoints] = loyaltyPoints
            it[UserTable.redeemedLoyaltyPoints] = loyaltyPoints
            it[UserTable.createdAt] = createdAt
        }
    }

   data class FlightSeatData(val flightId: Int, val seatId: Int)

    fun seedFlightSeats() {
        if (FlightSeatTable.selectAll().limit(1).count() > 0) return

        // Get flights with their aircraft_id
        val flights = FlightTable.selectAll().map { flight ->
            flight[FlightTable.id] to flight[FlightTable.aircraftId]
        }
        
        // Get seats grouped by aircraft_id
        val seatsByAircraft = SeatTable.selectAll().groupBy { seat ->
            seat[SeatTable.aircraftId]
        }.mapValues { (_, seats) ->
            seats.map { it[SeatTable.id] }
        }

        val data = mutableListOf<FlightSeatData>()
        
        for ((flightId, aircraftId) in flights) {
            seatsByAircraft[aircraftId]?.forEach { seatId ->
                data.add(FlightSeatData(flightId, seatId))
            }
        }

        // Batch insert in chunks to avoid memory issues
        data.chunked(10000).forEach { chunk ->
            FlightSeatTable.batchInsert(chunk) { seatData ->
                this[FlightSeatTable.flightId] = seatData.flightId
                this[FlightSeatTable.seatId] = seatData.seatId
            }
        }
    }
}