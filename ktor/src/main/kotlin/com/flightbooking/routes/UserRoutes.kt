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
        val params = call.receiveParameters() // will need changing in future to limit user input (Currently needs every field entered not just name email and password)

        val firstname = params["firstname"]
        val lastname = params["lastname"]
        val email = params["email"]
        val passwordHash = params["passwordHash"]
        
        if(firstname == null || lastname == null || email == null || passwordHash == null){
            call.respond(HttpStatusCode.BadRequest, "Missing required field")
            return@post
        }

        val user = User(
            id = 0,
            firstname = firstname,
            lastname = lastname,
            email = email,
            passwordHash = passwordHash, //STILL TO HASH
            role = "USER",
            createdAt = LocalDateTime.now()
        )

        userRepository.createUser(user)
        call.respond(HttpStatusCode.Created, "User registered successfully")
    }
    
}