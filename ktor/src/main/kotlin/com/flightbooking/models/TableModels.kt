package com.flightbooking.models

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.UserRole
import java.math.BigDecimal
import java.time.LocalDateTime

data class User(
    val id: Int,
    val firstname: String?,
    val lastname: String?,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val loyaltyPoints: Int,
    val redeemedLoyaltyPoints: Int,
    val createdAt: LocalDateTime,
)

data class Booking(
    val id: Int,
    val bookingReference: String,
    val userId: Int,
    val flightId: Int,
    val returnFlightId: Int?,
    val status: BookingStatus,
    val createdAt: LocalDateTime,
)

data class Flight(
    val id: Int,
    val flightCode: String,
    val departureAirportId: Int,
    val arrivalAirportId: Int,
    val aircraftId: Int,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val minPrice: BigDecimal,
    val status: FlightStatus,
)

data class Aircraft(
    val id: Int,
    val type: String,
    val numOfSeats: Int,
)

data class Airport(
    val id: Int,
    val name: String,
    val code: String,
    val city: String,
    val country: String,
)

data class Passenger(
    val id: Int,
    val bookingId: Int,
    val firstname: String,
    val lastname: String,
    val passportCode: String?,
)

data class Payment(
    val id: Int,
    val bookingId: Int,
    val amount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val paymentMethod: PaymentMethod,
    val transactionId: String,
    val createdAt: LocalDateTime,
    val refundAmount: BigDecimal?,
    val refundDate: LocalDateTime?,
)

data class Complaint(
    val id: Int,
    val userId: Int,
    val message: String,
    val status: ComplaintStatus,
    val createdAt: LocalDateTime,
    // Admin handling part
    val adminReply: String? = null,
    val repliedAt: LocalDateTime? = null,
    val repliedByUserId: Int? = null,
)

data class Seat(
    val id: Int,
    val aircraftId: Int,
    val seatNumber: String,
    val seatClass: SeatClass,
)

data class TicketAssignment(
    val id: Int,
    val passengerId: Int,
    val flightSeatId: Int,
    val ticketPrice: BigDecimal,
    val seatNumber: String,
)

data class FlightSeat(
    val id: Int,
    val flightId: Int,
    val seatId: Int,
)
