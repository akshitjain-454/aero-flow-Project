package com.flightbooking.sessions

import kotlinx.serialization.Serializable
import com.flightbooking.enums.UserRole

@Serializable
data class VerificationSession(
    val email: String,
    val OTP: String
)