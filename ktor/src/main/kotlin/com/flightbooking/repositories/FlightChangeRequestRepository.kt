package com.flightbooking.repositories

import com.flightbooking.models.FlightChangeRequest
import com.flightbooking.tables.FlightChangeRequestTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class FlightChangeRequestRepository {

    fun createFlightChangeRequest(bookingId: Int, requestFlightId: Int) {
        transaction {
            FlightChangeRequestTable.insert {
                it[FlightChangeRequestTable.bookingId] = bookingId
                it[FlightChangeRequestTable.requestedFlightId] = requestedFlightId
                it[FlightChangeRequestTable.status] = "Pending" 
                it[FlightChangeRequestTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getRequestByBookingId(bookingId:Int): List<FlightChangeRequest> {
        return transaction {
            FlightChangeRequestTable
                .select { FlightChangeRequestTable.bookingId eq bookingId }
                .orderBy(FlightChangeRequestTable.createdAt, SortOrder.DESC)
                .map { resultRowToFlightChangeRequest(it) }
        }
    }

    fun resultRowToFlightChangeRequest(row: ResultRow): FlightChangeRequest {
        return FlightChangeRequest(
            id = row[FlightChangeRequestTable.id],
            bookingId = row[FlightChangeRequestTable.bookingId],
            requestFlightId = row[FlightChangeRequestTable.requestedFlightId],
            status = row[FlightChangeRequestTable.status],
            createdAt = row[FlightChangeRequestTable.createdAt]
        )
    }
}
