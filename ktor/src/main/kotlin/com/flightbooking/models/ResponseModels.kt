package com.flightbooking.models

import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.ComplaintStatus
import java.time.LocalDateTime
import java.math.BigDecimal

data class SeatAvailability(
    val flightSeatId: Int,
    val seatNumber: String,
    val seatClass: SeatClass,
    val available: Boolean
)

data class SelectedSeat(
    val flightSeatId: Int,
    val seatNumber: String,
    val seatClass: SeatClass,
    val passenger: Passenger
)

data class TicketInfo(
    val passengerName: String,
    val bookingReference: String,
    val seatNumber: String,
    val seatClass: SeatClass,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val dateTime: LocalDateTime
)

data class FlightInfo(
    val flightCode: String,
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
    val returnFlightCode: String?,
    val bookingStatus: BookingStatus,
    val numOfPassengers: Long,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val returnDepartureAirportNameCode: String?,
    val returnArrivalAirportNameCode: String?,
    val departureTime: LocalDateTime,
    val returnDepartureTime: LocalDateTime?,
    //management ui combine
    val flightStatus: FlightStatus,
    val amountPaid: BigDecimal?
)

data class BookingsPerFlightReport(
    val flightId: Int,
    val flightCode: String,
    val departureAirportId: Int,
    val arrivalAirportId: Int,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val flightStatus: FlightStatus,
    val bookingCount: Long,
    val aircraftType: String
)

data class FlightAvailabilitySummary(
    val flightId: Int,
    val flightCode: String,
    val departureAirportId: Int,
    val arrivalAirportId: Int,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val flightStatus: FlightStatus,
    val totalSeats: Long,
    val bookedSeats: Long,
    val availableSeats: Long,
    val aircraftType: String
)

data class CancelledBookingSummary(
    val bookingId: Int,
    val bookingReference: String,
    val userId: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val flightId: Int,
    val flightCode: String,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val status: BookingStatus,
    val createdAt: LocalDateTime,
    val aircraftType: String
)

data class CancelledFlightSummary(
    val flightId: Int,
    val flightCode: String,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val status: FlightStatus,
    val aircraftType: String
)

data class FlightChangeLogInfo(
    val id: Int,
    val flightId: Int,
    val flightCode: String,
    val oldDepartureAirportNameCode: String,
    val newDepartureAirportNameCode: String,
    val oldArrivalAirportNameCode: String,
    val newArrivalAirportNameCode: String,
    val oldDepartureTime: LocalDateTime,
    val newDepartureTime: LocalDateTime,
    val oldArrivalTime: LocalDateTime,
    val newArrivalTime: LocalDateTime,
    val changedAt: LocalDateTime,
    val flightStatus: FlightStatus,
    val aircraftType: String,
    val changedByUserId: Int?,
    val changedByName: String?
)

data class MostPopularRouteReport(
    val departureAirportId: Int,
    val arrivalAirportId: Int,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val bookingCount: Long
)

data class PeakBookingTimeReport(
    val bookingHour: String,
    val bookingCount: Long
)

data class ReservationSummary(
    val bookingId: Int,
    val bookingReference: String,
    val userId: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val flightId: Int,
    val flightCode: String,
    val departureAirportNameCode: String,
    val arrivalAirportNameCode: String,
    val departureTime: LocalDateTime,
    val bookingStatus: BookingStatus,
    val createdAt: LocalDateTime,
    val amountPaid: BigDecimal?,
    val aircraftType: String
)

data class ComplaintSummary(
    val id: Int,
    val userId: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val message: String,
    val status: ComplaintStatus,
    val createdAt: LocalDateTime
)
