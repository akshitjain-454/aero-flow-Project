package com.flightbooking.routes 

import com.flightbooking.models.User
import com.flightbooking.repositories.UserRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.enums.UserRole
import com.flightbooking.respondPebble
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt
import io.ktor.server.sessions.*
// --- PEBBLE IMPORTS ---
import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader


fun Route.userRoutes() {

    val userRepository = UserRepository()

    post("/register") {
        val params = call.receiveParameters()

        val firstname = params["firstname"]?.trim()
        val lastname  = params["lastname"]?.trim()
        val email     = params["email"]?.trim()
        val password  = params["password"]

        if (firstname == null || lastname == null || email == null || password == null) {
            //return@post call.respond(HttpStatusCode.BadRequest, "Missing required field")
            return@post call.respondPebble("register.peb", mapOf("error" to "Missing required fields")) //shouldnt happen unless frontend is bypassed
        }

        val atIndex = email.indexOf('@')

        if(atIndex <= 0 || email.indexOf('.', atIndex) <= atIndex + 1 ||  email.last() != '.') {
            return@post call.respondPebble("register.peb", mapOf("error" to "Email isn't in valid format")) //shouldnt happen unless frontend is bypassed
        }

        if (userRepository.getUserByEmail(email) != null) {
            return@post call.respondPebble("register.peb", mapOf("error" to "Already a member. Please Sign in instead!"))
        }

        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

        val user = User(
            id = 0,
            firstname = firstname,
            lastname  = lastname,
            email     = email,
            passwordHash = passwordHash,
            role      = UserRole.USER,
            createdAt = LocalDateTime.now()
        )

        userRepository.createUser(user)

        // Fetch back the saved user to get the real DB-assigned ID
        val savedUser = userRepository.getUserByEmail(email)!!

        call.sessions.set(UserSession(userId = savedUser.id, role = savedUser.role, initials = "${savedUser.firstname.first().uppercaseChar()}${savedUser.lastname.first().uppercaseChar()}"))
        call.respondRedirect("/")
    }

    post("/login") {
        val params = call.receiveParameters()

        val email    = params["email"]?.trim()
        val password = params["password"]

        if (email == null || password == null) {
            //return@post call.respond(HttpStatusCode.BadRequest, "Missing email or password")
            return@post call.respondPebble("login.peb", mapOf("error" to "Missing email or password")) //shouldnt happen unless frontend is bypassed
        }

        val atIndex = email.indexOf('@')

        if(atIndex <= 0 || email.indexOf('.', atIndex) <= atIndex + 1 ||  email.last() != '.') {
            return@post call.respondPebble("login.peb", mapOf("error" to "Email isn't in valid format")) //shouldnt happen unless frontend is bypassed
        }

        val user = userRepository.getUserByEmail(email)

        if (user == null) {
            return@post call.respondPebble("login.peb", mapOf("error" to "Invalid Credentials"))
        }

        if (BCrypt.checkpw(password, user.passwordHash)) {
            call.sessions.set(UserSession(userId = user.id, role = user.role, initials = "${user.firstname.first().uppercaseChar()}${user.lastname.first().uppercaseChar()}"))
            call.respondRedirect("/")
        } else {
            call.respondPebble("login.peb", mapOf("error" to "Invalid Password"))
        }
    }

    get("/login") {
        call.respondPebble("login.peb")
    }

    get("/register") {
        call.respondPebble("register.peb")
    }
    
    post("/logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}