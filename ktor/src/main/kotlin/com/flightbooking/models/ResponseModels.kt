package com.flightbooking.models

import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.BookingStatus
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
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val dateTime: LocalDateTime
)

data class FlightInfo(
<<<<<<< HEAD
    val flightCode: String ,
=======
    val flightCode: String,
>>>>>>> 1988d42 (Added flight code to flightInfo Dan and Akshit)
    val departureAirport: String,
    val departureAirportCode: String,
    val arrivalAirport: String,
    val arrivalAirportCode: String,
    val departureTime: LocalDateTime,
    val priceFrom: BigDecimal
)

data class BookingInfo(
    val bookingReference: String,
    val flightCode: String,
    val bookingStatus: BookingStatus,
    val numOfPassengers: Long,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val amountPaid: BigDecimal?
)