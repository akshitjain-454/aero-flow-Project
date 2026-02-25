package com.flightbooking.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object Users : IntIdTable() {
    val firstname = varchar("firstname", 255)
    val lastname = varchar("lastname", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
}