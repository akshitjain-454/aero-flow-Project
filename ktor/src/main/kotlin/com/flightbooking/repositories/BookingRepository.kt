package com.flightbooking.repositories

import com.flightbooking.models.Booking
import com.flightbooking.tables.BookingTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class BookingRepository {

    fun getBookingById(bookingId: Int): Booking {
        return transaction {
            val row = BookingTable
                .select { BookingTable.id eq bookingId }
                .single()

            resultRowToBooking(row)
        }
    }

    fun bookingExists(bookingId: Int): Boolean {
        return transaction {
            BookingTable
                .select { BookingTable.id eq bookingId }
                .count() > 0
        }
    }

    fun cancelBooking(bookingId: Int): Boolean {
        return transaction {
            val updatedRows = BookingTable.update({ BookingTable.id eq bookingId }) {
                it[status] = "CANCELLED"
            }
            updatedRows > 0
        }
    }

    fun resultRowToBooking(row: ResultRow): Booking {
        return Booking(
            id = row[BookingTable.id],
            bookingReference = row[BookingTable.bookingReference],
            userId = row[BookingTable.userId],
            flightId = row[BookingTable.flightId],
            status = row[BookingTable.status],
            createdAt = row[BookingTable.createdAt]
        )
    }
}
