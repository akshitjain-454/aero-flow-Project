package com.flightbooking.sessions

import com.flightbooking.enums.UserRole

data class UserSession(
    val userId: Int,
    val role: UserRole
)