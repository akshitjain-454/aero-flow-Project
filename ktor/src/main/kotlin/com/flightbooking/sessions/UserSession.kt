package com.flightbooking.sessions

import com.flightbooking.enums.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: Int,
    val role: UserRole,
    val initials: String,
)
