package com.flightbooking.models

import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.FlightStatus
import java.time.LocalDateTime
import java.math.BigDecimal

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

data class FlightInfo(
    val departureAirport: String,
    val departureAirportCode: String,
    val arrivalAirport: String,
    val arrivalAirportCode: String,
    val departureTime: LocalDateTime,
    val priceFrom: BigDecimal
)

data class BookingsPerFlightReport(
    val flightId: Int,
    val flightCode: String,
    val departureAirportId: Int,
    val arrivalAirportId: Int,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val flightStatus: FlightStatus,
    val bookingCount: Long
)