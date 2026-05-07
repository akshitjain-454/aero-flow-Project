package com.flightbooking.tables

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.UserRole
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.date

private const val DEFAULT_VARCHAR_LENGTH = 100
private const val EMAIL_VARCHAR_LENGTH = 255
private const val ENUM_NAME_LENGTH = 30
private const val AIRPORT_CODE_LENGTH = 5
private const val BOOKING_REFERENCE_LENGTH = 100
private const val MESSAGE_LENGTH = 300
private const val ADMIN_REPLY_LENGTH = 1000
private const val TRANSACTION_ID_LENGTH = 255
private const val DECIMAL_PRECISION = 10
private const val DECIMAL_SCALE = 2

object UserTable : Table("User") {
    val id = integer("user_id").autoIncrement()
    val firstname = varchar("firstname", DEFAULT_VARCHAR_LENGTH).nullable()
    val lastname = varchar("lastname", DEFAULT_VARCHAR_LENGTH).nullable()
    val email = varchar("email", EMAIL_VARCHAR_LENGTH).uniqueIndex()
    val passwordHash = varchar("password_hash", EMAIL_VARCHAR_LENGTH)
    val role = enumerationByName("role", ENUM_NAME_LENGTH, UserRole::class)
    val loyaltyPoints = integer("loyalty_points")
    val redeemedLoyaltyPoints = integer("redeemed_loyalty_points")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object BookingTable : Table("Booking") {
    val id = integer("booking_id").autoIncrement()
    val bookingReference = varchar("booking_reference", BOOKING_REFERENCE_LENGTH).uniqueIndex()
    val userId = integer("user_id").references(UserTable.id)
    val flightId = integer("flight_id").references(FlightTable.id)
    val returnFlightId = integer("return_flight_id").references(FlightTable.id).nullable()
    val status = enumerationByName("status", ENUM_NAME_LENGTH, BookingStatus::class)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object FlightTable : Table("Flight") {
    val id = integer("flight_id").autoIncrement()
    val flightCode = varchar("flight_code", BOOKING_REFERENCE_LENGTH).uniqueIndex()
    val departureAirportId = integer("departure_airport_id").references(AirportTable.id)
    val arrivalAirportId = integer("arrival_airport_id").references(AirportTable.id)
    val aircraftId = integer("aircraft_id").references(AircraftTable.id)
    val departureTime = datetime("departure_time")
    val arrivalTime = datetime("arrival_time")
    val minPrice = decimal("min_price", DECIMAL_PRECISION, DECIMAL_SCALE)
    val status = enumerationByName("status", ENUM_NAME_LENGTH, FlightStatus::class)

    override val primaryKey = PrimaryKey(id)
}

object AircraftTable : Table("Aircraft") {
    val id = integer("aircraft_id").autoIncrement()
    val type = varchar("type", DEFAULT_VARCHAR_LENGTH)
    val numOfSeats = integer("num_of_seats")

    override val primaryKey = PrimaryKey(id)
}

object AirportTable : Table("Airport") {
    val id = integer("airport_id").autoIncrement()
    val name = varchar("name", DEFAULT_VARCHAR_LENGTH)
    val code = varchar("code", AIRPORT_CODE_LENGTH).uniqueIndex()
    val city = varchar("city", DEFAULT_VARCHAR_LENGTH)
    val country = varchar("country", DEFAULT_VARCHAR_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

object PassengerTable : Table("Passenger") {
    val id = integer("passenger_id").autoIncrement()
    val bookingId = integer("booking_id").references(BookingTable.id)
    val firstname = varchar("firstname", DEFAULT_VARCHAR_LENGTH)
    val lastname = varchar("lastname", DEFAULT_VARCHAR_LENGTH)
    val passportCode = varchar("passport_code", DEFAULT_VARCHAR_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

object PaymentTable : Table("Payment") {
    val id = integer("payment_id").autoIncrement()
    val bookingId = integer("booking_id").references(BookingTable.id)
    val amount = decimal("amount", DECIMAL_PRECISION, DECIMAL_SCALE)
    val paymentStatus = enumerationByName("payment_status", ENUM_NAME_LENGTH, PaymentStatus::class)
    val paymentMethod = enumerationByName("payment_method", ENUM_NAME_LENGTH, PaymentMethod::class)
    val transactionId = varchar("transaction_id", TRANSACTION_ID_LENGTH)
    val createdAt = datetime("created_at")
    val refundAmount = decimal("refund_amount", DECIMAL_PRECISION, DECIMAL_SCALE).nullable()
    val refundDate = datetime("refund_date").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ComplaintTable : Table("Complaint") {
    val id = integer("complaint_id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id)
    val message = varchar("message", MESSAGE_LENGTH)
    val status = enumerationByName("status", ENUM_NAME_LENGTH, ComplaintStatus::class)
    val createdAt = datetime("created_at")

    // Admin handling part
    val adminReply = varchar("admin_reply", ADMIN_REPLY_LENGTH).nullable()
    val repliedAt = datetime("replied_at").nullable()
    val repliedByUserId = integer("replied_by_user_id").references(UserTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object SeatTable : Table("Seat") {
    val id = integer("seat_id").autoIncrement()
    val aircraftId = integer("aircraft_id").references(AircraftTable.id)
    val seatNumber = varchar("seat_number", DEFAULT_VARCHAR_LENGTH)
    val seatClass = enumerationByName("seat_class", ENUM_NAME_LENGTH, SeatClass::class)

    override val primaryKey = PrimaryKey(id)
}

object TicketAssignmentTable : Table("TicketAssignment") {
    val id = integer("ticket_assignment_id").autoIncrement()
    val passengerId = integer("passenger_id").references(PassengerTable.id)
    val flightSeatId = integer("flight_seat_id").references(FlightSeatTable.id).uniqueIndex()
    val ticketPrice = decimal("ticketPrice", DECIMAL_PRECISION, DECIMAL_SCALE)
    val seatNumber = varchar("seat_number", DEFAULT_VARCHAR_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

object FlightSeatTable : Table("FlightSeat") {
    val id = integer("flight_seat_id").autoIncrement()
    val flightId = integer("flight_id").references(FlightTable.id)
    val seatId = integer("seat_id").references(SeatTable.id)

    init {
        uniqueIndex(flightId, seatId)
    }

    override val primaryKey = PrimaryKey(id)
}

object FlightChangeLogTable : Table("FlightChangeLog") {
    val id = integer("flight_change_log_id").autoIncrement()
    val flightId = integer("flight_id").references(FlightTable.id)
    val oldDepartureAirportId = integer("old_departure_airport_id").references(AirportTable.id)
    val newDepartureAirportId = integer("new_departure_airport_id").references(AirportTable.id)
    val oldArrivalAirportId = integer("old_arrival_airport_id").references(AirportTable.id)
    val newArrivalAirportId = integer("new_arrival_airport_id").references(AirportTable.id)
    val oldDepartureTime = datetime("old_departure_time")
    val newDepartureTime = datetime("new_departure_time")
    val oldArrivalTime = datetime("old_arrival_time")
    val newArrivalTime = datetime("new_arrival_time")
    val changedAt = datetime("changed_at")
    val changedByUserId = integer("changed_by_user_id").references(UserTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object FlightInfoRequestTable : Table("FlightInfoRequest") {
    val id = integer("flight_info_request_id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id)
    val bookingId = integer("booking_id").references(BookingTable.id)
    val requestType = enumerationByName("request_type", ENUM_NAME_LENGTH, FlightInfoRequestType::class)
    val status = enumerationByName("status", ENUM_NAME_LENGTH, FlightInfoRequestStatus::class)
    val passengerId = integer("passenger_id").references(PassengerTable.id).nullable()
    val newFirstname = varchar("new_firstname", DEFAULT_VARCHAR_LENGTH).nullable()
    val newLastname = varchar("new_lastname", DEFAULT_VARCHAR_LENGTH).nullable()
    val newPassportCode = varchar("new_passport_code", DEFAULT_VARCHAR_LENGTH).nullable()
    val requestedFlightCode = varchar("requested_flight_code", DEFAULT_VARCHAR_LENGTH).nullable()
    val message = varchar("message", ADMIN_REPLY_LENGTH).nullable()
    val adminReply = varchar("admin_reply", ADMIN_REPLY_LENGTH).nullable()
    val createdAt = datetime("created_at")
    val handledAt = datetime("handled_at").nullable()
    val handledByUserId = integer("handled_by_user_id").references(UserTable.id).nullable()
    val requestedDepartureDate = date("requested_departure_date").nullable()

    override val primaryKey = PrimaryKey(id)
}
