package com.flightbooking.repositories

import com.flightbooking.models.Complaint
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.enums.ComplaintStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import com.flightbooking.models.ComplaintSummary
import com.flightbooking.tables.UserTable

class ComplaintRepository {

    fun createComplaint(userId: Int, message: String): Complaint = transaction {
        val now = LocalDateTime.now()

        val complaintId = ComplaintTable.insert {
            it[ComplaintTable.userId] = userId
            it[ComplaintTable.message] = message
            it[ComplaintTable.status] = ComplaintStatus.OPEN
            it[ComplaintTable.createdAt] = now
            //Admin handling part
            it[ComplaintTable.adminReply] = null
            it[ComplaintTable.repliedAt] = null
            it[ComplaintTable.repliedByUserId] = null
        } get ComplaintTable.id

        Complaint(
            id = complaintId,
            userId = userId,
            message = message,
            status = ComplaintStatus.OPEN,
            createdAt = now,
            //Admin handling part
            adminReply = null,
            repliedAt = null,
            repliedByUserId = null
        )
    }

    fun getComplaintsByUserId(userId: Int): List<Complaint> = transaction {
        ComplaintTable
            .select { ComplaintTable.userId eq userId }
            .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
            .map { resultRowToComplaint(it) }
    }
    //admin part,getAllComplaints(),getComplaintById(),updateComplaintStatus()
    fun getAllComplaints(): List<ComplaintSummary> = transaction {
        ComplaintTable
            .join(
                UserTable,
                JoinType.INNER,
                ComplaintTable.userId,
                UserTable.id
            )
            .select { ComplaintTable.status neq ComplaintStatus.CLOSED } //The closed status is not displayed
            .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
            .map { row ->
                val repliedByUserId = row[ComplaintTable.repliedByUserId]

                ComplaintSummary(
                    id = row[ComplaintTable.id],
                    userId = row[ComplaintTable.userId],
                    firstname = row[UserTable.firstname],
                    lastname = row[UserTable.lastname],
                    email = row[UserTable.email],
                    message = row[ComplaintTable.message],
                    status = row[ComplaintTable.status],
                    createdAt = row[ComplaintTable.createdAt],
                    //Admin handling part
                    adminReply = row[ComplaintTable.adminReply],
                    repliedAt = row[ComplaintTable.repliedAt],
                    repliedByUserId = repliedByUserId,
                    repliedByName = getUserNameById(repliedByUserId)
                    )
            }
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

    fun handleComplaint(id: Int, newStatus: ComplaintStatus, reply: String?, adminUserId: Int): Complaint? = transaction {
        val existingComplaint = ComplaintTable
            .select { ComplaintTable.id eq id }
            .singleOrNull() ?: return@transaction null

            ComplaintTable.update({ ComplaintTable.id eq id }) {
                    it[status] = newStatus

                if (!reply.isNullOrBlank()) {
                    it[adminReply] = reply.trim()
                    it[repliedAt] = LocalDateTime.now()
                    it[repliedByUserId] = adminUserId
                }
            }
            ComplaintTable
                .select { ComplaintTable.id eq id }
                .map { resultRowToComplaint(it) }
                .singleOrNull()
    }

    private fun getUserNameById(userId: Int?): String? {
        if (userId == null) return null

        return UserTable
            .select { UserTable.id eq userId }
            .map { it[UserTable.firstname] + " " + it[UserTable.lastname] }
            .singleOrNull()
    }

    private fun resultRowToComplaint(row: ResultRow): Complaint {
        return Complaint(
            id = row[ComplaintTable.id],
            userId = row[ComplaintTable.userId],
            message = row[ComplaintTable.message],
            status = row[ComplaintTable.status],
            createdAt = row[ComplaintTable.createdAt],
            //Admin handling part
            adminReply = row[ComplaintTable.adminReply],
            repliedAt = row[ComplaintTable.repliedAt],
            repliedByUserId = row[ComplaintTable.repliedByUserId]
        )
    }
}