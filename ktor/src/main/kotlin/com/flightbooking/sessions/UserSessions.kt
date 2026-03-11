package com.flightbooking.sessions

import kotlinx.serialization.Serializable
import com.flightbooking.enums.UserRole

@Serializable
data class UserSession(
    val userId: Int,
    val role: UserRole
)