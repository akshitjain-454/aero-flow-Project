package com.flightbooking.routes 

import com.flightbooking.services.NotificationService
import com.flightbooking.services.NotificationEvent
import com.flightbooking.models.User
import com.flightbooking.repositories.UserRepository
import com.flightbooking.sessions.*
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

    route("/register") {
        get() {
            call.respondPebble("getemail.peb") 
        }
        post() {
            val params = call.receiveParameters()

            val email = params["email"]?.trim() ?: return@post call.respondPebble("register.peb", mapOf("error" to "Missing email")) //shouldnt happen unless frontend is bypassed

            val atIndex = email.indexOf('@')

            if(atIndex <= 0 || email.indexOf('.', atIndex) <= atIndex + 1 ||  email.last() == '.') {
                return@post call.respondPebble("getemail.peb", mapOf("error" to "Email isn't in valid format")) //shouldnt happen unless frontend is bypassed
            }

            if (userRepository.getUserByEmail(email) != null) {
                return@post call.respondPebble("getemail.peb", mapOf("error" to "Already a member. Please Sign in instead!"))
            }
            val otp = (100000..999999).random().toString()
            userRepository.sendEmail(email, "Verify Your Email", "Your one time code is: $otp")
            call.sessions.set(VerificationSession(email, otp, false))
            call.respondRedirect("/register/verify")

        }
        get("/verify") {
            val vfySession = call.sessions.get<VerificationSession>() ?: return@get call.respondRedirect("/register")
            call.respondPebble("verify.peb")
        }
        post("/verify") {
            val vfySession = call.sessions.get<VerificationSession>() ?: return@post call.respondRedirect("/register")
            val params = call.receiveParameters()

            val otpParam = params["otp_param"]?.trim() ?: return@post call.respondPebble("verify.peb", mapOf("error" to "No code entered"))

            if(otpParam != vfySession.otp) {
                return@post call.respondPebble("verify.peb", mapOf("error" to "The code you entered was incorrect"))
            }
            call.sessions.set(vfySession.copy(verified = true))
            call.respondRedirect("/register/details")
        }
        get("/details") {
            val vfySession = call.sessions.get<VerificationSession>() ?: return@get call.respondRedirect("/register")
            if(vfySession.verified != true) { return@get call.respondRedirect("/register/verify") }
            call.respondPebble("register.peb")
        }

        post("/details") {
            val vfySession = call.sessions.get<VerificationSession>() ?: return@post call.respondPebble("register.peb", mapOf("error" to "No verification session"))
            val params = call.receiveParameters()

            val firstname = params["firstname"]?.trim()?.takeIf { it.isNotBlank() }
            val lastname  = params["lastname"]?.trim()?.takeIf { it.isNotBlank() }
            val email     = vfySession.email
            val password  = params["password"]?.trim()
            val confirmedPassword = params["confirmed_password"]?.trim()

            if (password == null || confirmedPassword == null) {
                //return@post call.respond(HttpStatusCode.BadRequest, "Missing required field")
                return@post call.respondPebble("register.peb", mapOf("error" to "Missing required fields")) //shouldnt happen unless frontend is bypassed
            }
            if (password != confirmedPassword) {    
                return@post call.respondPebble("register.peb", mapOf("error" to "Passwords do not match")) //shouldnt happen unless frontend is bypassed
            }

            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            val user = User(
                id = 0,
                firstname = firstname,
                lastname  = lastname,
                email     = email,
                passwordHash = passwordHash,
                role      = UserRole.USER,
                loyaltyPoints = 0,
                createdAt = LocalDateTime.now()
            )

            userRepository.createUser(user)

            // Fetch back the saved user to get the real DB-assigned ID
            val savedUser = userRepository.getUserByEmail(email)!!
            
            val initials = userRepository.getInitialsByUser(savedUser)

            call.sessions.set(UserSession(userId = savedUser.id, role = savedUser.role, initials = initials))
            call.respondRedirect("/")
        }
    }

    route("/overview") {
        get() {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@get call.respondRedirect("/login")
            
            // Fetch bookings for the quick-view section
            val bookingRepository = com.flightbooking.repositories.BookingRepository()
            val allBookings = bookingRepository.getBookingsByUserId(session.userId)
                .map { bookingRepository.getBookingInfoByBooking(it) }
            
            // Grab just the first 2 bookings for the dashboard preview
            val recentBookings = allBookings.take(2)

            val points = user.loyaltyPoints

            val currentTier = when {
                points >= 25000 -> "Platinum"
                points >= 10000 -> "Gold"
                points >= 5000  -> "Silver"
                else            -> "Bronze"
            }
            val nextTierName = when {
                points >= 25000 -> "Max Tier"
                points >= 10000 -> "Platinum"
                points >= 5000  -> "Gold"
                else            -> "Silver"
            }
            
            val nextMilestone = when {
            points >= 25000 -> points // Maxed out
            points >= 10000 -> 25000
            points >= 5000  -> 10000
            else            -> 5000
            }

            val pointsToNext = if (nextMilestone > points) nextMilestone - points else 0
            
            call.respondPebble("overview.peb", mapOf(
                "user" to user,
                "recentBookings" to recentBookings,
                "totalBookings" to allBookings.size,
                "currentTier" to currentTier,
                "nextTierName" to nextTierName,
                "pointsToNext" to pointsToNext
            ))
        }
        post("/add_name") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respondRedirect("/login")
            val params = call.receiveParameters()

            val firstname = params["firstname"]?.trim()?.takeIf { it.isNotBlank() }
            val lastname  = params["lastname"]?.trim()?.takeIf { it.isNotBlank() }

            userRepository.updateNameForUser(user.id, firstname, lastname)
            call.respondRedirect("/overview")
        }
    }
    route("/settings") {
        get() {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@get call.respondRedirect("/login")
            call.respondPebble("settings.peb", mapOf("user" to user))
        }
        post("/update_name") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respondRedirect("/login")
            val params = call.receiveParameters()

            val firstname = params["firstname"]?.trim()?.takeIf { it.isNotBlank() }
            val lastname  = params["lastname"]?.trim()?.takeIf { it.isNotBlank() }

            userRepository.updateNameForUser(user.id, firstname, lastname)
            call.respondRedirect("/settings")
        }
        post("/change_password") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respondRedirect("/login")
            val params = call.receiveParameters()

            val password = params["password"]
            val newPassword  = params["new_password"]?.trim()
            val confirmNewPassword = params["confirm_new_password"]?.trim()

            if (password == null || newPassword == null || confirmNewPassword == null) {
                return@post call.respondPebble("settings.peb", mapOf("error" to "Missing required fields"))
            }
            
            if (BCrypt.checkpw(password, user.passwordHash) == false) {
                return@post call.respondPebble("settings.peb", mapOf("error" to "Old password is incorrect"))
            }

            if (newPassword != confirmNewPassword) {    
                return@post call.respondPebble("settings.peb", mapOf("error" to "Passwords do not match")) //shouldnt happen unless frontend is bypassed
            }

            val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())

            userRepository.changePasswordForUser(user.id, newPasswordHash)
            call.respondRedirect("/settings")
        }
        post("/delete_account") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val user = userRepository.getUserById(session.userId) ?: return@post call.respondRedirect("/login")
            
            userRepository.deleteUser(user.id) //No turning back
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
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

        if(atIndex <= 0 || email.indexOf('.', atIndex) <= atIndex + 1 ||  email.last() == '.') {
            return@post call.respondPebble("login.peb", mapOf("error" to "Email isn't in valid format")) //shouldnt happen unless frontend is bypassed
        }

        val user = userRepository.getUserByEmail(email)

        if (user == null) {
            return@post call.respondPebble("login.peb", mapOf("error" to "Invalid Credentials"))
        }

        if (BCrypt.checkpw(password, user.passwordHash)) {
            val initials = userRepository.getInitialsByUser(user)
            call.sessions.set(UserSession(userId = user.id, role = user.role, initials = initials))
            if (user.role == UserRole.ADMIN) {
                call.respondRedirect("/admin")
            } else {
                call.respondRedirect("/")
            }
        } else {
            call.respondPebble("login.peb", mapOf("error" to "Invalid Password"))
        }
    }

    get("/login") {
        call.respondPebble("login.peb")
    }
    
    post("/logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
    get("/logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}