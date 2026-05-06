package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.Properties

class UserRepository {
    fun createUser(user: User) {
        transaction {
            UserTable.insert {
                it[firstname] = user.firstname
                it[lastname] = user.lastname
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[role] = user.role
                it[loyaltyPoints] = user.loyaltyPoints
                it[redeemedLoyaltyPoints] = user.redeemedLoyaltyPoints
                it[createdAt] = LocalDateTime.now()
            }
        }
    }

    fun deleteUser(userId: Int) {
        transaction {
            UserTable.deleteWhere { SqlExpressionBuilder.run { UserTable.id eq userId } }
        }
    }

    fun updateNameForUser(
        userId: Int,
        firstname: String?,
        lastname: String?,
    ) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.firstname] = firstname
                it[UserTable.lastname] = lastname
            }
        }
    }

    fun changePasswordForUser(
        userId: Int,
        newPasswordHash: String,
    ) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.passwordHash] = newPasswordHash
            }
        }
    }

    fun getUserByEmail(email: String): User? =
        transaction {
            UserTable
                .select { UserTable.email eq email }
                .map { resultRowToUser(it) }.singleOrNull()
        }

    fun getInitialsByUser(user: User): String =
        transaction {
            if (user.firstname != null && user.lastname != null) {
                "${user.firstname.first().uppercaseChar()}${user.lastname.first().uppercaseChar()}"
            } else if ((user.firstname == null && user.lastname != null)) {
                "${user.lastname.first().uppercaseChar()}"
            } else if ((user.firstname != null && user.lastname == null)) {
                "${user.firstname.first().uppercaseChar()}"
            } else {
                user.email.take(2).uppercase().ifEmpty { "U" }
            }
        }

    fun sendEmail(
        email: String,
        subject: String,
        body: String,
    ) {
        val props =
            Properties().apply {
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
            }

        val emailsession =
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication("aeroflow.noreplys@gmail.com", "hgenhbdcynhmbmuz")
                },
            )

        MimeMessage(emailsession).apply {
            setFrom(InternetAddress("aeroflow.noreplys@gmail.com"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            setSubject(subject)
            setText(body)
            Transport.send(this)
        }
    }

    fun resultRowToUser(row: ResultRow): User {
        return User(
            id = row[UserTable.id],
            firstname = row[UserTable.firstname],
            lastname = row[UserTable.lastname],
            email = row[UserTable.email],
            passwordHash = row[UserTable.passwordHash],
            role = row[UserTable.role],
            loyaltyPoints = row[UserTable.loyaltyPoints],
            redeemedLoyaltyPoints = row[UserTable.redeemedLoyaltyPoints],
            createdAt = row[UserTable.createdAt],
        )
    }

    fun getUserById(id: Int): User? =
        transaction {
            UserTable
                .select { UserTable.id eq id }
                .map { resultRowToUser(it) }.singleOrNull()
        }
}
