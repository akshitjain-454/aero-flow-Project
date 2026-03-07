package com.flightbooking.models

import java.time.LocalDateTime
import java.math.BigDecimal

data class User(

    val id: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val passwordHash: String,
    val role: String,
    val createdAt: LocalDateTime
)


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

data class FlightChangeRequest (

  val id: Int,
  val bookingId: Int,
  val requestFlightId: Int,
  val status: String,
  val createdAt : LocalDateTime
)

data class PassengerInfoChangeRequest (

  val id: Int,
  val bookingId: Int,
  val newFirstname: String,
  val newLastname: String,
  val newPassportCode: String,
  val status: String,
  val createdAt : LocalDateTime
)

data class Notification (

  val id: Int,
  val userId: Int,
  val message: String,
  val isRead: Boolean,
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
