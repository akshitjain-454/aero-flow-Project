package com.flightbooking.routes

import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.models.ReservationSummary
import com.flightbooking.models.User
import com.flightbooking.repositories.AdminRepository
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.repositories.UserRepository
import com.flightbooking.respondPebble
import com.flightbooking.services.NotificationEvent
import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Registers administrator-only routes for the management dashboard.
 *
 * These routes allow administrators to,
 * view operational reports,
 * manage complaints,
 * handle customer flight information requests,
 * search reservations,
 * update flight schedules, and update flight statuses.
 *
 * Most routes require an active user session with the ADMIN role. Unauthenticated users are redirected to the login page,
 * while non-admin users receive a forbidden response.
 */
fun Route.adminRoutes() {
    val complaintRepository = ComplaintRepository()
    val adminRepository = AdminRepository()
    val userRepository = UserRepository()

    route("/admin") {
        // Renders the main administrator management dashboard.
        get {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admins only"),
                )
            }

            call.respondPebble("management.peb")
        }
        // Returns all customer complaints for administrator review.
        get("/complaints") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val complaints = complaintRepository.getAllComplaints()
            call.respond(complaints)
        }
        // Returns all customer flight information change requests.
        get("/flight-info-requests") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val requests = adminRepository.getAllFlightInfoRequests()
            call.respond(
                mapOf(
                    "requestType" to "flight_info_requests",
                    "totalRequests" to requests.size,
                    "results" to requests,
                ),
            )
        }
        // Returns flight availability information with optional route and date filters.
        get("/flights/availability") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val report = adminRepository.getFlightAvailabilityReport(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "flight_availability",
                    "totalFlightsInReport" to report.size,
                    "results" to report,
                ),
            )
        }
        // Returns cancelled bookings with optional route and date filters.
        get("/bookings/cancelled") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }
            val cancelledBookings = adminRepository.getCancelledBookings(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "cancelled_bookings",
                    "totalCancelledBookings" to cancelledBookings.size,
                    "results" to cancelledBookings,
                ),
            )
        }
        // Returns cancelled flights with optional route and date filters.
        get("/flights/cancelled") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val cancelledFlights = adminRepository.getCancelledFlights(fromCodes, toCodes, date)
            call.respond(
                mapOf(
                    "reportType" to "cancelled_flights",
                    "totalCancelledFlights" to cancelledFlights.size,
                    "results" to cancelledFlights,
                ),
            )
        }
        // Searches reservations by customer name, email, or booking reference.
        get("/customer-search") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val query = call.request.queryParameters["q"]?.trim()

            if (query.isNullOrBlank()) {
                return@get call.respond(
                    mapOf(
                        "requestType" to "customer_search",
                        "query" to "",
                        "totalResults" to 0,
                        "results" to emptyList<ReservationSummary>(),
                    ),
                )
            }
            val results = adminRepository.searchReservationsByCustomer(query)

            call.respond(
                mapOf(
                    "requestType" to "customer_search",
                    "query" to query,
                    "totalResults" to results.size,
                    "results" to results,
                ),
            )
        }
        // Updates the status of a customer complaint.
        post("/complaints/{id}/status") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@post call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val complaintIdText = call.parameters["id"]
            val complaintId = complaintIdText?.toIntOrNull()

            if (complaintId == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid complaint id"),
                )
            }

            val params = call.receiveParameters()
            val statusText = params["status"]?.trim()?.uppercase()
            val reply = params["reply"]?.trim()

            if (statusText.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing status"),
                )
            }

            val newStatus =
                try {
                    ComplaintStatus.valueOf(statusText)
                } catch (error: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Invalid status",
                            "allowedStatuses" to ComplaintStatus.values().map { it.name },
                        ),
                    )
                }

            val complaint = complaintRepository.getComplaintById(complaintId)

            if (complaint == null) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found"),
                )
            }

            val updatedComplaint = complaintRepository.updateComplaintStatus(complaintId, newStatus)

            if (updatedComplaint == null) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update complaint status"),
                )
            } else {
                val complainer = userRepository.getUserById(complaint.userId)
                if (complainer != null) {
                    if (complainer.role != UserRole.ADMIN) {
                        NotificationService.send(
                            NotificationEvent(complaint.userId, "You have an update to your complaint(s)", "info"),
                        )
                    } else {
                        NotificationService.send(
                            NotificationEvent(complaint.userId, "You have an update to your admin complaint(s)", "info"),
                        )
                    }
                }
            }
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Complaint status updated successfully",
                    "complaintId" to updatedComplaint.id,
                    "oldStatus" to complaint.status.name,
                    "newStatus" to updatedComplaint.status.name,
                ),
            )
        }
        // Handles a complaint by applying a status and optional admin reply.
        post("/complaints/{id}/handle") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@post call.respondRedirect("/login")
            }

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val complaintId = call.parameters["id"]?.toIntOrNull()
            if (complaintId == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid complaint id"),
                )
            }

            val complaint = complaintRepository.getComplaintById(complaintId)
            if (complaint == null) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found"),
                )
            }

            val complainer = userRepository.getUserById(complaint.userId)
            val params = call.receiveParameters()
            val statusText = params["status"]?.trim()?.uppercase()
            val reply = params["reply"]?.trim()

            if (statusText.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing complaint status"),
                )
            }

            val newStatus =
                try {
                    ComplaintStatus.valueOf(statusText)
                } catch (error: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Invalid complaint status",
                            "allowedStatuses" to listOf("OPEN", "IN_REVIEW", "RESOLVED"),
                        ),
                    )
                }

            if (newStatus == ComplaintStatus.CLOSED) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Please use OPEN, IN_REVIEW, or RESOLVED"),
                )
            }

            val updatedComplaint =
                complaintRepository.handleComplaint(
                    id = complaintId,
                    newStatus = newStatus,
                    reply = reply,
                    adminUserId = session.userId,
                )

            if (updatedComplaint == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found"),
                )
            } else {
                if (complainer != null) {
                    if (complainer.role != UserRole.ADMIN) {
                        NotificationService.send(NotificationEvent(complaint.userId, "You have an update to your complaint(s)", "info"))
                    } else {
                        NotificationService.send(
                            NotificationEvent(complaint.userId, "You have an update to your admin complaint(s)", "info"),
                        )
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Complaint handled successfully",
                        "complaint" to updatedComplaint,
                    ),
                )
            }
        }
        // Approves or rejects a customer flight information change request.
        post("/flight-info-requests/{id}/handle") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@post call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val requestId =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request id")
            val customerRequest = adminRepository.getFlightInfoRequestsByRequestID(requestId)
            if (customerRequest == null) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Request not found"),
                )
            }
            val user = userRepository.getUserById(customerRequest.userId)
            val params = call.receiveParameters()
            val status =
                try {
                    FlightInfoRequestStatus.valueOf(
                        params["status"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing status",
                        ),
                    )
                } catch (error: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid request status")
                }
            val reply = params["reply"]?.trim()
            try {
                val success =
                    adminRepository.handleFlightInfoRequest(
                        requestId = requestId,
                        newStatus = status,
                        adminReply = reply,
                        adminUserId = session.userId,
                    )
                if (!success) {
                    return@post call.respond(HttpStatusCode.NotFound, "Request not found")
                } else {
                    if (user != null) {
                        if (user.role != UserRole.ADMIN) {
                            NotificationService.send(NotificationEvent(user.id, "You have an update to your request(s)", "info"))
                        } else {
                            NotificationService.send(
                                NotificationEvent(user.id, "You have an update to your admin request(s)", "info"),
                            )
                        }
                    }
                    call.respond(
                        mapOf("message" to "Flight information request handled successfully"),
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Could not handle request")),
                )
            }
        }
        // Returns the number of active bookings grouped by flight.
        get("/reports/bookings-per-flight") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val report = adminRepository.getBookingsPerFlightReport()

            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight",
                    "totalFlightsInReport" to report.size,
                    "results" to report,
                ),
            )
        }
        // Returns the booking report for one specific flight code.
        get("/reports/bookings-per-flight/{flightCode}") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val flightCode = call.parameters["flightCode"]?.trim()

            if (flightCode.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing flightCode"),
                )
            }

            val report =
                adminRepository.getBookingsPerFlightByFlightCode(flightCode)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Flight not found"),
                    )

            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight_single",
                    "result" to report,
                ),
            )
        }
        // Returns the most popular routes based on booking count.
        get("/reports/most-popular-routes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val report = adminRepository.getMostPopularRoutesReport()
            call.respond(
                mapOf(
                    "reportType" to "most_popular_routes",
                    "totalRoutesInReport" to report.size,
                    "results" to report,
                ),
            )
        }
        // Returns the peak booking hours.
        get("/reports/peak-booking-times") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val report = adminRepository.getPeakBookingTimesReport()
            call.respond(
                mapOf(
                    "reportType" to "peak_booking_times",
                    "totalTimeSlotsInReport" to report.size,
                    "results" to report,
                ),
            )
        }
        // Returns all recorded flight schedule changes.
        get("/flights/changes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }
            val changes = adminRepository.getAllFlightChanges(fromCodes, toCodes, date)

            call.respond(
                mapOf(
                    "reportType" to "flight_changes",
                    "totalChanges" to changes.size,
                    "results" to changes,
                ),
            )
        }
        // Returns the schedule change history for a single flight.
        get("/flights/{id}/changes") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val flightId =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid flight id"),
                    )
            val changes = adminRepository.getFlightChangesByFlightId(flightId)
            call.respond(
                mapOf(
                    "reportType" to "flight_changes_single",
                    "flightId" to flightId,
                    "totalChanges" to changes.size,
                    "results" to changes,
                ),
            )
        }
        // Returns reservation summaries with optional filters.
        get("/reservations") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val status =
                params["status"]?.trim()?.uppercase()?.let {
                    try {
                        BookingStatus.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid status",
                                "allowedStatuses" to BookingStatus.values().map { value -> value.name },
                            ),
                        )
                    }
                }

            val reservations =
                adminRepository.getAllReservations(
                    fromCodes = fromCodes,
                    toCodes = toCodes,
                    date = date,
                    status = status,
                )

            call.respond(
                mapOf(
                    "reportType" to "all_reservations",
                    "totalReservations" to reservations.size,
                    "filters" to
                        mapOf(
                            "from" to fromCodes,
                            "to" to toCodes,
                            "date" to date?.toString(),
                            "status" to status?.name,
                        ),
                    "results" to reservations,
                ),
            )
        }
        // Updates the route and departure/arrival times of a flight.
        post("/flights/{id}/update-schedule") {
            val session = call.sessions.get<UserSession>()

            if (session == null || session.role != UserRole.ADMIN) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@post
            }

            val flightId = call.parameters["id"]?.toIntOrNull()

            if (flightId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid flight id"))
                return@post
            }

            val params = call.receiveParameters()
            val departureAirportCode = params["departureAirportCode"]?.trim()?.uppercase()
            val arrivalAirportCode = params["arrivalAirportCode"]?.trim()?.uppercase()
            val departureTimeText = params["departureTime"]
            val arrivalTimeText = params["arrivalTime"]

            if (departureAirportCode.isNullOrBlank() ||
                arrivalAirportCode.isNullOrBlank() ||
                departureTimeText.isNullOrBlank() ||
                arrivalTimeText.isNullOrBlank()
            ) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing departure airport code, arrival airport code, departure time, or arrival time"),
                )
                return@post
            }

            val departureAirportId = adminRepository.getAirportIdByCode(departureAirportCode)
            val arrivalAirportId = adminRepository.getAirportIdByCode(arrivalAirportCode)

            if (departureAirportId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Departure airport code not found: $departureAirportCode"))
                return@post
            }

            if (arrivalAirportId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Arrival airport code not found: $arrivalAirportCode"))
                return@post
            }

            try {
                val departureTime = LocalDateTime.parse(departureTimeText)
                val arrivalTime = LocalDateTime.parse(arrivalTimeText)
                val updatedFlight =
                    adminRepository.updateFlightSchedule(
                        flightId = flightId,
                        newDepartureAirportId = departureAirportId,
                        newArrivalAirportId = arrivalAirportId,
                        newDepartureTime = departureTime,
                        newArrivalTime = arrivalTime,
                        changedByUserId = session.userId,
                    )

                if (updatedFlight == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Flight not found"))
                } else {
                    // NotificationService.send(NotificationEvent("You have an update to your booking(s)", "info"))
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "message" to "Flight schedule updated successfully",
                            "flight" to updatedFlight,
                        ),
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid date time format. Use yyyy-MM-ddTHH:mm"),
                )
            }
        }
        // Updates the operational status of a flight.
        post("/flights/{id}/status") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@post call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }

            val flightId =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid flight id"))
            val params = call.receiveParameters()
            val newStatus =
                try {
                    FlightStatus.valueOf(params["status"] ?: "")
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid flight status"))
                }

            val updatedFlight = adminRepository.updateFlightStatus(flightId, newStatus)

            if (updatedFlight == null) {
                return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Flight not found"))
            }
            // NotificationService.send(NotificationEvent("You have an update to your booking(s)", "info"))
            call.respond(
                mapOf("message" to "Flight status updated successfully", "flight" to updatedFlight),
            )
        }
        get("/create_admin") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            call.respondPebble("addAdmin.peb")
        }

        post("/create_admin") {
            val session =
                call.sessions.get<UserSession>()
                    ?: return@post call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only"),
                )
            }
            val params = call.receiveParameters()

            val firstname = params["firstname"]?.trim()?.takeIf { it.isNotBlank() }
            val lastname = params["lastname"]?.trim()?.takeIf { it.isNotBlank() }
            val email =
                params["email"]
                    ?: return@post call.respondPebble("management.peb", mapOf("error" to "Missing email for admin member"))
            val password = params["password"]?.trim()
            val confirmedPassword = params["confirmed_password"]?.trim()

            if (UserRepository().getUserByEmail(email) != null) {
                return@post call.respondPebble("addAdmin.peb", mapOf("error" to "Email already Registered. Please Sign in instead!"))
            }
            if (password == null || confirmedPassword == null) {
                // return@post call.respond(HttpStatusCode.BadRequest, "Missing required field")
                return@post call.respondPebble(
                    "addAdmin.peb",
                    mapOf("error" to "Missing required fields"),
                ) // shouldnt happen unless frontend is bypassed
            }
            if (password != confirmedPassword) {
                return@post call.respondPebble(
                    "addAdmin.peb",
                    mapOf("error" to "Passwords do not match"),
                ) // shouldnt happen unless frontend is bypassed
            }

            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            val admin =
                User(
                    id = 0,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    passwordHash = passwordHash,
                    role = UserRole.ADMIN,
                    loyaltyPoints = 0,
                    redeemedLoyaltyPoints = 0,
                    createdAt = LocalDateTime.now(),
                )

            adminRepository.createAdmin(admin)

            call.respondRedirect("/admin")
        }
    }
}
