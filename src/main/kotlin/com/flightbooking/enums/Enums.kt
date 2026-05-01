package com.flightbooking.enums

enum class BookingStatus {
    CREATED,
    CONFIRMED,
    CANCELLED
}

enum class FlightStatus {
    SCHEDULED,
    DELAYED,
    ARRIVED,
    CANCELLED
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

enum class PaymentMethod {
    CARD,
    PAYPAL,
    BANK_TRANSFER
}

enum class UserRole {
    USER,
    ADMIN
}

enum class SeatClass {
    ECONOMY,
    BUSINESS,
    FIRST
}

enum class ComplaintStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    CLOSED
}