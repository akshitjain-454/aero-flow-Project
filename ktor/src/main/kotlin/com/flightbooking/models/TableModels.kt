package com.flightbooking.models

data class UsersTable(
    val id: Int = 0,
    val roomId: Int,
    val userId: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val passwordHash: String
)