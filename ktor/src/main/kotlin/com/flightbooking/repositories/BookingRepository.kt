package com.flightbooking.repositories

import com.flightbooking.models.Booking
import com.flightbooking.tables.BookingTable
import com.flightbooking.models.Passenger
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.UserTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.enums.BookingStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class BookingRepository {

    fun createBooking(userId: Int, flightId: Int): Booking = transaction { 
        val now = LocalDateTime.now()
        val reference = generateBookingReference()

        val bookingId = BookingTable.insert{
            it[bookingReference] = reference
            it[BookingTable.userId] = userId
            it[BookingTable.flightId]= flightId
            it[status] = BookingStatus.CREATED
            it[createdAt] = now
        } get BookingTable.id

        Booking (
            id = bookingId,
            bookingReference = reference,
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CREATED,
            createdAt = now
        )
    }

    fun addPassenger(bookingId: Int, firstname: String, lastname: String, passportCode: String?): Passenger = transaction {
        val passengerId = PassengerTable.insert {
            it[PassengerTable.bookingId] = bookingId
            it[PassengerTable.firstname] = firstname
            it[PassengerTable.lastname]= lastname
            it[PassengerTable.passportCode] = passportCode
        } get PassengerTable.id

        Passenger (
            id = passengerId,
            bookingId = bookingId,
            firstname = firstname,
            lastname = lastname,
            passportCode = passportCode
        ) 
    }
    
    fun getBookingsByUserId(userId: Int): List<Booking> = transaction {
        BookingTable
            .select { BookingTable.userId eq userId }
            .map { resultRowToBooking(it) }
    }

    fun getBookingByReference(bookingReference: String): Booking? = transaction {
        BookingTable
            .select { BookingTable.bookingReference eq bookingReference }
            .map { resultRowToBooking(it) }.singleOrNull()
    }

    fun cancelBooking(bookingReference: String): Booking? = transaction {
        val booking = BookingTable.select { BookingTable.bookingReference eq bookingReference }
            .map { resultRowToBooking(it) }
            .singleOrNull() ?: return@transaction null

        if (booking.status == BookingStatus.CANCELLED) {
            return@transaction booking
        }

        BookingTable.update({ BookingTable.bookingReference eq bookingReference }) {
            it[status] = BookingStatus.CANCELLED
        }

        booking.copy(status = BookingStatus.CANCELLED)
    }

    fun resultRowToBooking(row: ResultRow): Booking {
        return Booking (
            id = row[BookingTable.id],
            bookingReference = row[BookingTable.bookingReference],
            userId = row[BookingTable.userId],
            flightId = row[BookingTable.flightId],
            status = row[BookingTable.status],
            createdAt = row[BookingTable.createdAt],
        )
    }

    fun generateBookingReference(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()
    }

}