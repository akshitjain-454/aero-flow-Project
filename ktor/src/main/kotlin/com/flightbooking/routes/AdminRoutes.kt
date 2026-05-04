package com.flightbooking.routes

import io.ktor.server.pebble.PebbleContent
import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.enums.FlightInfoRequestStatus
import com.flightbooking.enums.FlightInfoRequestType
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.repositories.AdminRepository
import com.flightbooking.sessions.UserSession
import com.flightbooking.respondPebble
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.LocalDateTime
import java.time.LocalDate
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus

fun Route.adminRoutes() {

    val complaintRepository = ComplaintRepository()
    val adminRepository = AdminRepository()
    val flightRepository = FlightRepository()

    route("/admin") {

        get {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admins only")
                )
            }
            
            call.respondPebble("management.peb")
        }

        get("/complaints") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val complaints = complaintRepository.getAllComplaints()
            call.respond(complaints)
        }

        get("/flight-info-requests") {
            val session = call.sessions.get<UserSession>()?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val requests = adminRepository.getAllFlightInfoRequests()
            call.respond(
                mapOf(
                    "requestType" to "flight_info_requests",
                    "totalRequests" to requests.size,
                    "results" to requests
                )
            )
        }

        get("/flights/availability") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
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
                    "results" to report
                )
            )
        }

        get("/bookings/cancelled") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
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
                    "results" to cancelledBookings
                )
            )
        }

        get("/flights/cancelled") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
        
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
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
                    "results" to cancelledFlights
                )
            )
        }

        post("/complaints/{id}/status") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@post call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }

            val complaintIdText = call.parameters["id"]
            val complaintId = complaintIdText?.toIntOrNull()

            if (complaintId == null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid complaint id")
                )
            }

            val params = call.receiveParameters()
            val statusText = params["status"]?.trim()?.uppercase()

            if (statusText.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing status")
                )
            }

            val newStatus = try {
                ComplaintStatus.valueOf(statusText)
            } catch (error: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Invalid status",
                        "allowedStatuses" to ComplaintStatus.values().map { it.name }
                    )
                )
            }

            val complaint = complaintRepository.getComplaintById(complaintId)

            if (complaint == null) {
                return@post call.respond(HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found")
                )
            }

            val updatedComplaint = complaintRepository.updateComplaintStatus(complaintId, newStatus)

            if (updatedComplaint == null) {
                return@post call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update complaint status")
                )
            }
            call.respond(HttpStatusCode.OK,
                mapOf(
                    "message" to "Complaint status updated successfully",
                    "complaintId" to updatedComplaint.id,
                    "oldStatus" to complaint.status.name,
                    "newStatus" to updatedComplaint.status.name
                )
            )
        }

        post("/complaints/{id}/handle") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@post call.respondRedirect("/login")
            }

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }

            val complaintId = call.parameters["id"]?.toIntOrNull()

            if (complaintId == null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid complaint id")
                )
            }

            val params = call.receiveParameters()
            val statusText = params["status"]?.trim()?.uppercase()
            val reply = params["reply"]?.trim()

            if (statusText.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing complaint status")
                )
            }

            val newStatus = try {
                ComplaintStatus.valueOf(statusText)
            } catch (error: IllegalArgumentException) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Invalid complaint status",
                        "allowedStatuses" to listOf("OPEN", "IN_REVIEW", "RESOLVED")
                    )
                )
            }

            if (newStatus == ComplaintStatus.CLOSED) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Please use OPEN, IN_REVIEW, or RESOLVED")
                )
            }

            val updatedComplaint = complaintRepository.handleComplaint(
                id = complaintId,
                newStatus = newStatus,
                reply = reply,
                adminUserId = session.userId
            )

            if (updatedComplaint == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Complaint not found")
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Complaint handled successfully",
                        "complaint" to updatedComplaint
                    )
                )
            }
        }

        post("/flight-info-requests/{id}/handle") {
            val session = call.sessions.get<UserSession>()?: return@post call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val requestId = call.parameters["id"]?.toIntOrNull()?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request id")
            val params = call.receiveParameters()
            val status = try {
                FlightInfoRequestStatus.valueOf(
                    params["status"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing status"
                    )
                )
            } catch (error: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid request status")
            }
            val reply = params["reply"]?.trim()
            try {
                val success = adminRepository.handleFlightInfoRequest(
                    requestId = requestId,
                    newStatus = status,
                    adminReply = reply,
                    adminUserId = session.userId
                )
                if (!success) {
                    return@post call.respond(HttpStatusCode.NotFound, "Request not found")
                }
                call.respond(
                    mapOf("message" to "Flight information request handled successfully")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Could not handle request"))
                )
            }
        }

        get("/reports/bookings-per-flight") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }

            val report = adminRepository.getBookingsPerFlightReport()

            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight",
                    "totalFlightsInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/reports/bookings-per-flight/{flightCode}") {
            val session = call.sessions.get<UserSession>()

            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }

            val flightCode = call.parameters["flightCode"]?.trim()
            
            if (flightCode.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest,
                mapOf("error" to "Missing flightCode")
                )
            }

            val report = adminRepository.getBookingsPerFlightByFlightCode(flightCode)
                ?: return@get call.respond(HttpStatusCode.NotFound,
                mapOf("error" to "Flight not found")
                )
                
            call.respond(
                mapOf(
                    "reportType" to "bookings_per_flight_single",
                    "result" to report
                )
            )
        }

        get("/reports/most-popular-routes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val report = adminRepository.getMostPopularRoutesReport()
            call.respond(
                mapOf(
                    "reportType" to "most_popular_routes",
                    "totalRoutesInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/reports/peak-booking-times") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }
            val report = adminRepository.getPeakBookingTimesReport()
            call.respond(
                mapOf(
                    "reportType" to "peak_booking_times",
                    "totalTimeSlotsInReport" to report.size,
                    "results" to report
                )
            )
        }

        get("/flights/changes") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                return@get call.respondRedirect("/login")
            }
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
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
                    "results" to changes
                )
            )
        }

        get("/flights/{id}/changes") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            if (session.role != UserRole.ADMIN) {
                return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("error" to "Admin only")
                )
            }
            val flightId = call.parameters["id"]?.toIntOrNull()?: return@get call.respond(HttpStatusCode.BadRequest,
            mapOf("error" to "Invalid flight id")
            )
            val changes = adminRepository.getFlightChangesByFlightId(flightId)
            call.respond(
                mapOf(
                    "reportType" to "flight_changes_single",
                    "flightId" to flightId,
                    "totalChanges" to changes.size,
                    "results" to changes
                )
            )
        }

        get("/reservations") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }

            val params = call.request.queryParameters
            val fromCodes = params.getAll("from")
            val toCodes = params.getAll("to")
            val date = params["date"]?.let { LocalDate.parse(it) }

            val status = params["status"]?.trim()?.uppercase()?.let {
                try {
                    BookingStatus.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Invalid status",
                            "allowedStatuses" to BookingStatus.values().map { value -> value.name }
                        )
                    )
                }
            }

            val reservations = adminRepository.getAllReservations(
                fromCodes = fromCodes,
                toCodes = toCodes,
                date = date,
                status = status
            )

            call.respond(
                mapOf(
                    "reportType" to "all_reservations",
                    "totalReservations" to reservations.size,
                    "filters" to mapOf(
                        "from" to fromCodes,
                        "to" to toCodes,
                        "date" to date?.toString(),
                        "status" to status?.name
                    ),
                    "results" to reservations
                )
            )
        }
        
        post("/flights/{id}/update-schedule") {
            val session = call.sessions.get<UserSession>()

            if (session == null || session.role != UserRole.ADMIN) {
                call.respond(HttpStatusCode.Forbidden,mapOf("error" to "Admin only"))
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

            if (departureAirportCode.isNullOrBlank() || arrivalAirportCode.isNullOrBlank() || departureTimeText.isNullOrBlank() || arrivalTimeText.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing departure airport code, arrival airport code, departure time, or arrival time"))
                return@post
            }

            val departureAirportId = adminRepository.getAirportIdByCode(departureAirportCode)
            val arrivalAirportId = adminRepository.getAirportIdByCode(arrivalAirportCode)

            if (departureAirportId == null) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Departure airport code not found: $departureAirportCode"))
                return@post
            }

            if (arrivalAirportId == null) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Arrival airport code not found: $arrivalAirportCode"))
                return@post
            }

            try {
                val departureTime = LocalDateTime.parse(departureTimeText)
                val arrivalTime = LocalDateTime.parse(arrivalTimeText)
                val updatedFlight = adminRepository.updateFlightSchedule(
                    flightId = flightId,
                    newDepartureAirportId = departureAirportId,
                    newArrivalAirportId = arrivalAirportId,
                    newDepartureTime = departureTime,
                    newArrivalTime = arrivalTime,
                    changedByUserId = session.userId
                )

                if (updatedFlight == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Flight not found"))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "message" to "Flight schedule updated successfully",
                            "flight" to updatedFlight
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid date time format. Use yyyy-MM-ddTHH:mm")
                )
            }
        }

        post("/flights/{id}/status") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")

            if (session.role != UserRole.ADMIN) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    mapOf("error" to "Admin only")
                )
            }

            val flightId = call.parameters["id"]?.toIntOrNull()?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid flight id"))
            val params = call.receiveParameters()
            val newStatus = try {
                FlightStatus.valueOf(params["status"] ?: "")
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Invalid flight status"))
            }

            val updatedFlight = adminRepository.updateFlightStatus(flightId, newStatus)

            if (updatedFlight == null) {
                return@post call.respond(HttpStatusCode.NotFound,mapOf("error" to "Flight not found"))
            }
            call.respond(mapOf("message" to "Flight status updated successfully","flight" to updatedFlight)

            )
        }
    }
}