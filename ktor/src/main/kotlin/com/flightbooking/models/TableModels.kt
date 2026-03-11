package com.flightbooking.models

import com.flightbooking.enums.*
import java.time.LocalDateTime
import java.math.BigDecimal

data class User(

    val id: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val createdAt: LocalDateTime
)


data class Booking (

  val id: Int,
  val bookingReference: String, 
  val userId: Int,
  val flightId: Int,
  val status: BookingStatus,
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
  val status: FlightStatus
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
  val paymentStatus: PaymentStatus,
  val paymentMethod: PaymentMethod,
  val transactionId: String,
  val createdAt: LocalDateTime,
  val refundAmount: BigDecimal?,
  val refundDate: LocalDateTime?
)

data class Complaint (

  val id: Int,
  val userId: Int,
  val message: String,
  val status: ComplaintStatus,
  val createdAt : LocalDateTime
)

data class Seat (

  val id: Int,
  val aircraftId: Int,
  val seatNumber: String,
  val seatClass: SeatClass
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
