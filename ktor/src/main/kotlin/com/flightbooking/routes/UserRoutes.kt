package com.flightbooking.routes 

import com.flightbooking.models.User
import com.flightbooking.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime

fun Route.userRoutes() {

    val userRepository = UserRepository()

    post("/sign_up"){
        val request = call.receive<User>() // will need changing in future to limit user input (Currently needs every field entered not just name email and password)

        val user = User(
            id = 0,
            firstname = request.firstname,
            lastname = request.lastname,
            email = request.email,
            passwordHash = request.passwordHash, //STILL TO HASH
            role = "USER",
            createdAt = LocalDateTime.now()
        )

        userRepository.createUser(user)
        call.respond(HttpStatusCode.Created, "User registered successfully")
    }
    
}