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


object BookingTable : Table("Booking") {

  val id = integer("booking_id").autoIncrement()
  val bookingReference = varchar("booking_reference", 100) 
  val userId = integer("user_id").references(UserTable.id)
  val flightId = integer("flight_id").references(FlightTable.id)
  val status: String,
  val createdAt: LocalDateTime
}

object FlightTable : {

  val id: Int,
  val flightCode: String,
  val departureAirportId: Int,
  val arrivalAirportId: Int,
  val aircraftId: Int, 
  val departureTime: LocalDateTime,
  val arrivalTime: LocalDateTime,
  val price: BigDecimal,
  val status: String
}

object AircraftTable : {

  val id: Int,
  val type: String,
  val numOfSeats: Int
}

object AirportTable : {

  val id: Int,
  val name: String,
  val code: String,
  val city: String,
  val country: String
}

object PassengerTable : {

  val id: Int,
  val bookingId: Int,
  val firstname: String,
  val lastname: String,
  val passportCode: String?
}

object PaymentTable : {

  val id: Int,
  val bookingId: Int,
  val amount: BigDecimal,
  val paymentStatus: String,
  val paymentMethod: String,
  val transactionId: String,
  val createdAt: LocalDateTime,
  val refundAmount: BigDecimal?,
  val refundDate: LocalDateTime?
}

object ComplaintTable : {

  val id: Int,
  val userId: Int,
  val message: String,
  val status: String,
  val createdAt : LocalDateTime
}

object SeatTable : {

  val id: Int,
  val aircraftId: Int,
  val seatNumber: String,
  val seatClass: String
}

object TicketAssignmentTable : {

  val id: Int,
  val passengerId: Int,
  val flightSeatId: Int
}

object FlightSeatTable : {

  val id: Int,
  val flightId: Int,
  val seatId: Int
}


