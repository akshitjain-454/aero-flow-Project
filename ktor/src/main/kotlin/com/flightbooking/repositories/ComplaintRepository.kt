package com.flightbooking.repositories
import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.models.Complaint
import com.flightbooking.models.ComplaintSummary
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/**
 * Provides database operations for customer complaints.
 *
 * This repository allows users to create and view their own complaints, while
 * administrators can view, update, and respond to complaints. Complaint data is
 * stored and retrieved using Exposed transactions.
 */
class ComplaintRepository {
    /**
     * Creates a new complaint for a user.
     *
     * New complaints are created with an OPEN status. Admin reply fields are set
     * to null because the complaint has not yet been handled by an administrator.
     *
     * @param userId: The ID of the user submitting the complaint.
     * @param message: The complaint message submitted by the user.
     * Return the newly created complaint.
     */
    fun createComplaint(
        userId: Int,
        message: String,
    ): Complaint =
        transaction {
            val now = LocalDateTime.now()

            val complaintId =
                ComplaintTable.insert {
                    it[ComplaintTable.userId] = userId
                    it[ComplaintTable.message] = message
                    it[ComplaintTable.status] = ComplaintStatus.OPEN
                    it[ComplaintTable.createdAt] = now
                    // Admin handling part
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
                // Admin handling part
                adminReply = null,
                repliedAt = null,
                repliedByUserId = null,
            )
        }

    /**
     * Retrieves all complaints submitted by a specific user.
     *
     * Complaints are returned in descending order by creation time, so the most recent complaint appears first.
     *
     * @param userId: The ID of the user whose complaints should be retrieved.
     * Return a list of complaints submitted by the user.
     */
    fun getComplaintsByUserId(userId: Int): List<Complaint> =
        transaction {
            ComplaintTable
                .select { ComplaintTable.userId eq userId }
                .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
                .map { resultRowToComplaint(it) }
        }
    // admin part,getAllComplaints(),getComplaintById(),updateComplaintStatus()

    /**
     * Retrieves all active complaints for administrator review.
     *
     * Closed complaints are excluded from the result. Each returned summary includes
     * the complaint details, the submitting user's name and email address, and any
     * existing administrator reply information.
     *
     * Return a list of non-closed complaint summaries ordered by creation time.
     */
    fun getAllComplaints(): List<ComplaintSummary> =
        transaction {
            ComplaintTable
                .join(
                    UserTable,
                    JoinType.INNER,
                    ComplaintTable.userId,
                    UserTable.id,
                )
                .select { ComplaintTable.status neq ComplaintStatus.CLOSED } // The closed status is not displayed
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
                        // Admin handling part
                        adminReply = row[ComplaintTable.adminReply],
                        repliedAt = row[ComplaintTable.repliedAt],
                        repliedByUserId = repliedByUserId,
                        repliedByName = getUserNameById(repliedByUserId),
                    )
                }
        }

    /**
     * Retrieves a single complaint by its ID.
     *
     * @param id: The complaint ID to search for.
     * Return the matching complaint, or null if no complaint exists with the given ID.
     */
    fun getComplaintById(id: Int): Complaint? =
        transaction {
            ComplaintTable
                .select { ComplaintTable.id eq id }
                .map { resultRowToComplaint(it) }
                .singleOrNull()
        }

    /**
     * Updates the status of an existing complaint.
     *
     * After the update, the complaint is retrieved again and returned with its
     * latest status.
     *
     * @param id: The ID of the complaint to update.
     * @param newStatus: The new status to apply to the complaint.
     * Return the updated complaint, or null if no complaint exists with the given ID.
     */
    fun updateComplaintStatus(
        id: Int,
        newStatus: ComplaintStatus,
    ): Complaint? =
        transaction {
            val updatedRows =
                ComplaintTable.update({ ComplaintTable.id eq id }) {
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

    /**
     * Handles a complaint by updating its status and optionally adding an admin reply.
     *
     * If a non-blank reply is provided, the reply text is trimmed and stored with
     * the time of reply and the ID of the administrator who handled the complaint.
     * If the complaint cannot be found, no update is made.
     *
     * @param id: The ID of the complaint to handle.
     * @param newStatus: The new status to apply to the complaint.
     * @param reply: Optional reply message written by the administrator.
     * @param adminUserId: The ID of the administrator handling the complaint.
     * Return the updated complaint, or null if no complaint exists with the given ID.
     */
    fun handleComplaint(
        id: Int,
        newStatus: ComplaintStatus,
        reply: String?,
        adminUserId: Int,
    ): Complaint? =
        transaction {
            val existingComplaint =
                ComplaintTable
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

    /**
     * Retrieves the full name of a user by their ID.
     *
     * @param userId: The user ID to search for, or null.
     * Return the user's full name, or null if the user ID is null or no user is found.
     */
    private fun getUserNameById(userId: Int?): String? {
        if (userId == null) return null

        return UserTable
            .select { UserTable.id eq userId }
            .map { it[UserTable.firstname] + " " + it[UserTable.lastname] }
            .singleOrNull()
    }

    /**
     * Converts a database result row into a Complaint model.
     *
     * @param row The database row returned from the complaints table.
     * Return a Complaint object containing the row data.
     */
    private fun resultRowToComplaint(row: ResultRow): Complaint {
        return Complaint(
            id = row[ComplaintTable.id],
            userId = row[ComplaintTable.userId],
            message = row[ComplaintTable.message],
            status = row[ComplaintTable.status],
            createdAt = row[ComplaintTable.createdAt],
            // Admin handling part
            adminReply = row[ComplaintTable.adminReply],
            repliedAt = row[ComplaintTable.repliedAt],
            repliedByUserId = row[ComplaintTable.repliedByUserId],
        )
    }
}
