package com.flightbooking.sessions

import kotlinx.serialization.Serializable

@Serializable
data class VerificationSession(
    val email: String,
    val otp: String,
    val verified: Boolean,
)
