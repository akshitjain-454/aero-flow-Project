package com.flightbooking.models

import com.flightbooking.enums.SeatClass
import java.time.LocalDateTime

data class SeatAvailability(
    val flightSeatId: Int,
    val seatNumber: String,
    val seatClass: SeatClass,
    val available: Boolean
)

data class TicketInfo(
    val passengerName: String,
    val bookingReference: String,
    val seatNumber: String,
    val departureAirport: String,
    val arrivalAirport: String,
    val dateTime: LocalDateTime
)