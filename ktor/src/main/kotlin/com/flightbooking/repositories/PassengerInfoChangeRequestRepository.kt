package com.flightbooking.repositories

import com.flightbooking.models.PassengerInfoChangeRequest
import com.flightbooking.tables.PassengerInfoChangeRequestTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class PassengerInfoChangeRequestRepository {

    fun createPassengerInfoChangeRequest(bookingId: Int, newFirstname:String, newLastname: String, newPassportCode: String) {
        transaction {
            PassengerInfoChangeRequestTable.insert {
                it[PassengerInfoChangeRequestTable.bookingId] = bookingId
                it[PassengerInfoChangeRequestTable.newFirstname] = newFirstname
                it[PassengerInfoChangeRequestTable.newLastname] = newLastname
                it[PassengerInfoChangeRequestTable.newPassportCode] = newPassportCode
                it[PassengerInfoChangeRequestTable.status] = "Pending" 
                it[PassengerInfoChangeRequestTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getRequestByBookingId(bookingId:Int): List<PassengerInfoChangeRequest> {
        return transaction {
            PassengerInfoChangeRequestTable
                .select { PassengerInfoChangeRequestTable.bookingId eq bookingId }
                .orderBy(PassengerInfoChangeRequestTable.createdAt, SortOrder.DESC)
                .map { resultRowToPassengerInfoChangeRequest(it) }
        }
    }

    fun resultRowToPassengerInfoChangeRequest(row: ResultRow): PassengerInfoChangeRequest {
        return PassengerInfoChangeRequest(
            id = row[PassengerInfoChangeRequestTable.id],
            bookingId = row[PassengerInfoChangeRequestTable.bookingId],
            newFirstname = row[PassengerInfoChangeRequestTable.newFirstname],
            newLastname = row[PassengerInfoChangeRequestTable.newLastname],
            newPassportCode = row[PassengerInfoChangeRequestTable.newPassportCode],
            status = row[PassengerInfoChangeRequestTable.status],
            createdAt = row[PassengerInfoChangeRequestTable.createdAt]
        )
    }
}
