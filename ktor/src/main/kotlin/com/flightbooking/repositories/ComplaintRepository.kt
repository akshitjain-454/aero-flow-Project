package com.flightbooking.repositories

import com.flightbooking.models.Complaint
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.enums.ComplaintStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ComplaintRepository {

    fun createComplaint(userId: Int, message: String): Complaint = transaction {
        val now = LocalDateTime.now()

        val complaintId = ComplaintTable.insert {
            it[ComplaintTable.userId] = userId
            it[ComplaintTable.message] = message
            it[ComplaintTable.status] = ComplaintStatus.OPEN
            it[ComplaintTable.createdAt] = now
        } get ComplaintTable.id

        Complaint(
            id = complaintId,
            userId = userId,
            message = message,
            status = ComplaintStatus.OPEN,
            createdAt = now
        )
    }

    fun getComplaintsByUserId(userId: Int): List<Complaint> = transaction {
        ComplaintTable
            .select { ComplaintTable.userId eq userId }
            .map { resultRowToComplaint(it) }
    }

    //Admin part
    fun getAllComplaints(): List<Complaint> = transaction {
        ComplaintTable
            .selectAll()
            .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
            .map { resultRowToComplaint(it) }
    }
    //Admin part
    fun updateComplaintStatus(complaintId: Int, newStatus: ComplaintStatus): Complaint? = transaction {
        val existingComplaint = ComplaintTable
            .select { ComplaintTable.id eq complaintId }
            .map { resultRowToComplaint(it) }
            .singleOrNull()
        if (existingComplaint == null) {
            return@transaction null
        }

        ComplaintTable.update({ ComplaintTable.id eq complaintId }) {
            it[status] = newStatus
        }
        existingComplaint.copy(status = newStatus)
    }

    private fun resultRowToComplaint(row: ResultRow): Complaint {
        return Complaint(
            id = row[ComplaintTable.id],
            userId = row[ComplaintTable.userId],
            message = row[ComplaintTable.message],
            status = row[ComplaintTable.status],
            createdAt = row[ComplaintTable.createdAt]
        )
    }
}