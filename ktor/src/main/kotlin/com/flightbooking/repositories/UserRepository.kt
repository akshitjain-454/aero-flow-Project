package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserRepository {

    fun createUser(user: User) = transaction {
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