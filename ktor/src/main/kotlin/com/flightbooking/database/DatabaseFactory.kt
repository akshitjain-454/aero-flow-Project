package com.flightbooking.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import com.flightbooking.models.Users

object DatabaseFactory {

    fun init() {
        Database.connect(
            url = "jdbc:sqlite:flight_app.db",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(Users)
        }
    }
}