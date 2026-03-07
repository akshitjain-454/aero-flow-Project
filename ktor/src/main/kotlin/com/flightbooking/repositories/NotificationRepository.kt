package com.flightbooking.repositories

import com.flightbooking.models.Notification
import com.flightbooking.tables.NotificationTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class NotificationRepository {

    fun createNotification(userId: Int, message: String) {
        transaction {
            NotificationTable.insert {
                it[NotificationTable.userId] = userId
                it[NotificationTable.message] = message
                it[NotificationTable.isRead] = false
                it[NotificationTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getNotificationsByUserId(userId: Int): List<Notification> {
        return transaction {
            NotificationTable
                .select { NotificationTable.userId eq userId }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .map { resultRowToNotification(it) }
        }
    }

    fun markNotificationAsRead(notificationId: Int): Boolean {
        return transaction {
            val updatedRows = NotificationTable.update({ NotificationTable.id eq notificationId }) {
                it[isRead] = true
            }
            updatedRows > 0
        }
    }

    fun resultRowToNotification(row: ResultRow): Notification {
        return Notification(
            id = row[NotificationTable.id],
            userId = row[NotificationTable.userId],
            message = row[NotificationTable.message],
            isRead = row[NotificationTable.isRead],
            createdAt = row[NotificationTable.createdAt]
        )
    }
}