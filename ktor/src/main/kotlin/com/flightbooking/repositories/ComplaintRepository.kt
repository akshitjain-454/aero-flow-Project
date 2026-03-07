package com.flightbooking.repositories

import com.flightbooking.models.Complaint
import com.flightbooking.tables.ComplaintTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ComplaintRepository {

    fun createComplaint(userId: Int, message:String) {
        transaction {
            ComplaintTable.insert {
                it[ComplaintTable.userId] = userId
                it[ComplaintTable.message] = message
                it[ComplaintTable.status] = "Pending" 
                it[ComplaintTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getComplaintsByUserId(userId:Int): List<Complaint> {
        return transaction {
            ComplaintTable
                .select { ComplaintTable.userId eq userId }
                .map { resultRowToComplaint(it) }
        }
    }

    fun resultRowToComplaint(row: ResultRow): Complaint {
        return Complaint(
            id = row[ComplaintTable.id],
            userId = row[ComplaintTable.userId],
            message = row[ComplaintTable.message],
            status = row[ComplaintTable.status],
            createdAt = row[ComplaintTable.createdAt]
        )
    }
}
