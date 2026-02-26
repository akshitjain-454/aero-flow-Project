package com.flightbooking.models

data class UsersTable(
    val userId: Int,
    val roomId: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val passwordHash: String
)