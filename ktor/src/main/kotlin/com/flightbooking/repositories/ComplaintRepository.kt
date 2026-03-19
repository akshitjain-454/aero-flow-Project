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
    //admin part：getAllComplaints()，getComplaintById(),updateComplaintStatus()
    fun getAllComplaints(): List<Complaint> = transaction {
        ComplaintTable
            .selectAll()
            .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
            .map { resultRowToComplaint(it) }
    }

    fun getComplaintById(id: Int): Complaint? = transaction {
        ComplaintTable
            .select { ComplaintTable.id eq id }
            .map { resultRowToComplaint(it) }
            .singleOrNull()
    }

    fun updateComplaintStatus(id: Int, newStatus: ComplaintStatus): Complaint? = transaction {
        val updatedRows = ComplaintTable.update({ ComplaintTable.id eq id }) {
            it[status] = newStatus
        }
        if (updatedRows == 0) {
            null
        } else {
            ComplaintTable
                .select { ComplaintTable.id eq id }
                .map { resultRowToComplaint(it) }
                .singleOrNull()
        }
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