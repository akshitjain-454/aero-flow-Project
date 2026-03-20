//Only for testing
package com.flightbooking.database.seed

import com.flightbooking.enums.UserRole
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime

object DefaultDataSeeder {

    fun seed() {
        val now = LocalDateTime.now()

        createUserIfMissing(
            firstname = "Normal",
            lastname = "User",
            email = "user@demo.com",
            rawPassword = "123456",
            role = UserRole.USER,
            createdAt = now
        )

        createUserIfMissing(
            firstname = "System",
            lastname = "Admin",
            email = "admin@demo.com",
            rawPassword = "admin123",
            role = UserRole.ADMIN,
            createdAt = now
        )
    }

    private fun createUserIfMissing(
        firstname: String,
        lastname: String,
        email: String,
        rawPassword: String,
        role: UserRole,
        createdAt: LocalDateTime
    ) {
        val exists = UserTable
            .selectAll()
            .any { row -> row[UserTable.email] == email }

        if (exists) return

        UserTable.insert {
            it[UserTable.firstname] = firstname
            it[UserTable.lastname] = lastname
            it[UserTable.email] = email
            it[UserTable.passwordHash] = BCrypt.hashpw(rawPassword, BCrypt.gensalt())
            it[UserTable.role] = role
            it[UserTable.createdAt] = createdAt
        }
    }
}