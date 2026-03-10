package com.flightbooking.repositories

import com.flightbooking.models.Booking
import com.flightbooking.tables.BookingTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class BookingRepository {

    fun createBooking(booking: Booking) { 
        transaction {
            BookingTable.insert{
                it[bookingReference] = booking.bookingReference
                it[status] = booking.status
                it[createdAt] = LocalDateTime.now()
            }
        }
    }

    fun filterBooking(bookingReference: String): Booking? = transaction {
        BookingTable
            .select{ BookingTable.bookingReference eq bookingReference }
            .map { resultRowToBooking(it) }.singleOrNull()
    }

    fun resultRowToBooking(row: ResultRow): Booking {
        return Booking(
            id = row[BookingTable.id],
            bookingReference = row[BookingTable.bookingReference],
            userId = row[BookingTable.userId],
            flightId = row[BookingTable.flightId],
            status = row[BookingTable.status],
            createdAt = row[BookingTable.createdAt],
        )
    }
}