package com.flightbooking.routes

import com.flightbooking.models.User
import com.flightbooking.repositories.UserRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.enums.UserRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime

fun Route.userRoutes() {

    val userRepository = UserRepository()

    post("/sign_up") {
        val params = call.receiveParameters()
        val firstname = params["firstname"]
        val lastname = params["lastname"]
        val email = params["email"]
        val password = params["password"]

        if (firstname == null || lastname == null || email == null || password == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required field")
            return@post
        }

        if (userRepository.getUserByEmail(email) != null) {
            call.respond(HttpStatusCode.BadRequest, "Email already registered")
            return@post
        }

        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

        val user = User(
            id = 0,
            firstname = firstname,
            lastname = lastname,
            email = email,
            passwordHash = passwordHash,
            role = UserRole.USER,
            createdAt = LocalDateTime.now()
        )

        userRepository.createUser(user)

        // set session
        call.sessions.set(UserSession(user.id, user.role))
        call.respond(HttpStatusCode.Created, "User registered successfully")
    }

    post("/login") {
        val params = call.receiveParameters()
        val email = params["email"]
        val password = params["password"]

        if (email == null || password == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing email or password")
            return@post
        }

        val user = userRepository.getUserByEmail(email)
        if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            return@post
        }

        call.sessions.set(UserSession(user.id, user.role))
        call.respond(HttpStatusCode.OK, "User login successful")
    }

    post("/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK, "Logged out successfully")
    }
}