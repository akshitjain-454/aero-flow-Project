package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserRepository {

    fun createUser(user: User) { 
        transaction {
            UserTable.insert{
                it[firstname] = user.firstname
                it[lastname] = user.lastname
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[role] = user.role
                it[createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getUserByEmail(email: String): User? = transaction {
        UserTable
            .select { UserTable.email eq email }
            .map { resultRowToUser(it) }.singleOrNull()
    }

    fun resultRowToUser(row: ResultRow): User {
        return User(
            id = row[UserTable.id],
            firstname = row[UserTable.firstname],
            lastname = row[UserTable.lastname],
            email = row[UserTable.email],
            passwordHash = row[UserTable.passwordHash],
            role = row[UserTable.role],
            createdAt = row[UserTable.createdAt]
        )
    }
}