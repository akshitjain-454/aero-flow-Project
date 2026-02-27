package com.flightbooking.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserTable : Table("User") {
    val id = integer("user_id").autoIncrement()
    val firstname = varchar("firstname", 100)
    val lastname = varchar("lastname", 100)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50)
    val createdAt: LocalDateTime

    override val primaryKey = PrimaryKey(id)
}


data class Booking (

  val id: Int,
  val bookingReference: String, 
  val userId: Int,
  val flightId: Int,
  val status: String,
  val createdAt: LocalDateTime
)

data class Flight (

  val id: Int,
  val flightCode: String,
  val departureAirportId: Int,
  val arrivalAirportId: Int,
  val aircraftId: Int, 
  val departureTime: LocalDateTime,
  val arrivalTime: LocalDateTime,
  val price: BigDecimal,
  val status: String
)

data class Aircraft (

  val id: Int,
  val type: String,
  val numOfSeats: Int
)

data class Airport (

  val id: Int,
  val name: String,
  val code: String,
  val city: String,
  val country: String
)

data class Passenger (

  val id: Int,
  val bookingId: Int,
  val firstname: String,
  val lastname: String,
  val passportCode: String?
)

data class Payment (

  val id: Int,
  val bookingId: Int,
  val amount: BigDecimal,
  val paymentStatus: String,
  val paymentMethod: String,
  val transactionId: String,
  val createdAt: LocalDateTime,
  val refundAmount: BigDecimal?,
  val refundDate: LocalDateTime?
)

data class Complaint (

  val id: Int,
  val userId: Int,
  val message: String,
  val status: String,
  val createdAt : LocalDateTime
)

data class Seat (

  val id: Int,
  val aircraftId: Int,
  val seatNumber: String,
  val seatClass: String
)

data class TicketAssignment (

  val id: Int,
  val passengerId: Int,
  val flightSeatId: Int
)

data class FlightSeat (

  val id: Int,
  val flightId: Int,
  val seatId: Int
)


