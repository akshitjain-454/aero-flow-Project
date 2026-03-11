package com.flightbooking.sessions

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: Int,
    val role: String
)