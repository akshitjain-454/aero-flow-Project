package com.flightbooking.tables

import org.jetbrains.exposed.sql.Table


object UsersTable : Table("Users") {
    val id = integer("user_id").autoIncrement()
    val firstname = varchar("firstname", 255)
    val lastname = varchar("lastname", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)

    override val primaryKey = PrimaryKey(id)
}