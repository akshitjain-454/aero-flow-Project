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
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}


object BookingTable : Table("Booking") {

  val id = integer("booking_id").autoIncrement()
  val bookingReference = varchar("booking_reference", 100).uniqueIndex() 
  val userId = integer("user_id").references(UserTable.id)
  val flightId = integer("flight_id").references(FlightTable.id)
  val status = varchar("status", 100)
  val createdAt = datetime("created_at")

  override val primaryKey = PrimaryKey(id)
}

object FlightTable : Table("Flight") {

  val id = integer("flight_id").autoIncrement()
  val flightCode = varchar("flight_code", 100)
  val departureAirportId = integer("departure_airport_id").references(AirportTable.id)
  val arrivalAirportId = integer("arrival_airport_id").references(AirportTable.id)
  val aircraftId = integer("aircraft_id").references(AircraftTable.id) 
  val departureTime = datetime("departure_time")
  val arrivalTime = datetime("arrival_time")
  val price = decimal("price", 10, 2)
  val status = varchar("status", 100)

  override val primaryKey = PrimaryKey(id)
}

object AircraftTable : Table("Aircraft") {

  val id = integer("aircraft_id").autoIncrement()
  val type = varchar("type", 100)
  val numOfSeats = integer("num_of_seats")

  override val primaryKey = PrimaryKey(id)
}

object AirportTable : Table("Airport") {

  val id = integer("airport_id").autoIncrement()
  val name = varchar("name", 100)
  val code = varchar("code", 100).uniqueIndex()
  val city = varchar("city", 100)
  val country = varchar("country", 100)

  override val primaryKey = PrimaryKey(id)
}

object PassengerTable : Table("Passenger") {

  val id = integer("passenger_id").autoIncrement()
  val bookingId = integer("booking_id").references(BookingTable.id)
  val firstname = varchar("firstname", 100)
  val lastname = varchar("lastname", 100)
  val passportCode = varchar("passport_code", 100).nullable()

  override val primaryKey = PrimaryKey(id)
}

object PaymentTable : Table("Payment") {

  val id = integer("payment_id").autoIncrement()
  val bookingId = integer("booking_id").references(BookingTable.id)
  val amount = decimal("amount", 10, 2)
  val paymentStatus = varchar("payment_status", 100)
  val paymentMethod = varchar("payment_method", 100)
  val transactionId = varchar("transaction_id", 255)
  val createdAt = datetime("created_at")
  val refundAmount = decimal("refund_amount", 10, 2).nullable()
  val refundDate = datetime("refund_date").nullable()

  override val primaryKey = PrimaryKey(id)
}

object ComplaintTable : Table("Complaint") {

  val id = integer("complaint_id").autoIncrement()
  val userId = integer("user_id").references(UserTable.id)
  val message = varchar("message", 300)
  val status = varchar("status", 100)
  val createdAt = datetime("created_at")

  override val primaryKey = PrimaryKey(id)
}

object SeatTable : Table("Seat") {

  val id = integer("seat_id").autoIncrement()
  val aircraftId = integer("aircraft_id").references(AircraftTable.id)
  val seatNumber = varchar("seat_number", 100)
  val seatClass = varchar("seat_class", 100)

  override val primaryKey = PrimaryKey(id)
}

object TicketAssignmentTable : Table("TicketAssignment") {

  val id = integer("ticket_assignment_id").autoIncrement()
  val passengerId = integer("passenger_id").references(PassengerTable.id)
  val flightSeatId = integer("flight_seat_id").references(FlightSeatTable.id).uniqueIndex()

  override val primaryKey = PrimaryKey(id)
}

object FlightSeatTable : Table("FlightSeat") {

  val id = integer("flight_seat_id").autoIncrement()
  val flightId = integer("flight_id").references(FlightTable.id)
  val seatId = integer("seat_id").references(SeatTable.id)
  init {uniqueIndex(flightId, seatId)}

  override val primaryKey = PrimaryKey(id)
}


