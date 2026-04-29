package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.Properties
import jakarta.mail.*
import jakarta.mail.internet.*

class UserRepository {

    fun createUser(user: User) { 
        transaction {
            UserTable.insert{
                it[firstname] = user.firstname
                it[lastname] = user.lastname
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[role] = user.role
                it[loyaltyPoints] = user.loyaltyPoints
                it[createdAt] = LocalDateTime.now()
            }
        }
    }

    fun getUserByEmail(email: String): User? = transaction {
        UserTable
            .select { UserTable.email eq email }
            .map { resultRowToUser(it) }.singleOrNull()
    }

    fun sendEmail(email: String, subject: String, body: String) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
        }

        val emailsession = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication("aetheria.noreply@gmail.com", "ibqimnkgpfermajt")
        })

        MimeMessage(emailsession).apply {
            setFrom(InternetAddress("aetheria.noreply@gmail.com"))
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
            createdAt = row[UserTable.createdAt]
        )
    }
    
    fun getUserById(id: Int): User? = transaction {
        UserTable
            .select { UserTable.id eq id }
            .map { resultRowToUser(it) }.singleOrNull()
    }

}