package com.flightbooking.models

import com.flightbooking.enums.SeatClass

data class SeatAvailability(
    val flightSeatId: Int,
    val seatNumber: String,
    val seatClass: SeatClass,
    val available: Boolean
)
