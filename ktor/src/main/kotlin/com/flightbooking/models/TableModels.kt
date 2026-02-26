package com.flightbooking.models

data class UsersTable(
    val id: Int,
    val firstname: String,
    val lastname: String,
    val email: String,
    val passwordHash: String
)