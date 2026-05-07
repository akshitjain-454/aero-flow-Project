import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.AdminRepository
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightChangeLogTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

class AdminRepositoryTest : StringSpec({

    lateinit var repository: AdminRepository
    var databaseFile: Path? = null

    beforeTest {
        // Create a temporary SQLite database for each test.
        databaseFile = Files.createTempFile("admin-repository-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        // Create only the tables needed for Part 1 admin repository tests.
        transaction {
            SchemaUtils.create(
                UserTable,
                AirportTable,
                AircraftTable,
                FlightTable,
                BookingTable,
                PaymentTable,
                PassengerTable,
                SeatTable,
                FlightSeatTable,
                TicketAssignmentTable,
                FlightChangeLogTable,
            )
        }

        repository = AdminRepository()
    }

    afterTest {
        // Drop tables after each test to keep the database clean.
        transaction {
            SchemaUtils.drop(
                FlightChangeLogTable,
                TicketAssignmentTable,
                FlightSeatTable,
                SeatTable,
                PassengerTable,
                PaymentTable,
                BookingTable,
                FlightTable,
                AircraftTable,
                AirportTable,
                UserTable,
            )
        }

        // Delete the temporary database file.
        databaseFile?.let {
            Files.deleteIfExists(it)
        }
    }

    fun createTestUser(
        email: String,
        role: UserRole = UserRole.ADMIN,
        firstname: String = "Admin",
        lastname: String = "User",
    ): Int =
        transaction {
            UserTable.insert {
                it[UserTable.firstname] = firstname
                it[UserTable.lastname] = lastname
                it[UserTable.email] = email
                it[UserTable.passwordHash] = "hashed-password"
                it[UserTable.role] = role
                it[UserTable.loyaltyPoints] = 0
                it[UserTable.redeemedLoyaltyPoints] = 0
                it[UserTable.createdAt] = LocalDateTime.now()
            } get UserTable.id
        }

    fun createAirport(
        name: String,
        code: String,
        city: String = "Test City",
        country: String = "Test Country",
    ): Int =
        transaction {
            AirportTable.insert {
                it[AirportTable.name] = name
                it[AirportTable.code] = code
                it[AirportTable.city] = city
                it[AirportTable.country] = country
            } get AirportTable.id
        }

    fun createAircraft(
        type: String = "Airbus A320",
        numberOfSeats: Int = 180,
    ): Int =
        transaction {
            AircraftTable.insert {
                it[AircraftTable.type] = type
                it[AircraftTable.numOfSeats] = numberOfSeats
            } get AircraftTable.id
        }

    fun createFlight(
        flightCode: String,
        departureAirportId: Int,
        arrivalAirportId: Int,
        aircraftId: Int,
        departureTime: LocalDateTime = LocalDateTime.of(2026, 5, 1, 9, 0),
        arrivalTime: LocalDateTime = LocalDateTime.of(2026, 5, 1, 11, 0),
        status: FlightStatus = FlightStatus.SCHEDULED,
    ): Int =
        transaction {
            FlightTable.insert {
                it[FlightTable.flightCode] = flightCode
                it[FlightTable.departureAirportId] = departureAirportId
                it[FlightTable.arrivalAirportId] = arrivalAirportId
                it[FlightTable.aircraftId] = aircraftId
                it[FlightTable.departureTime] = departureTime
                it[FlightTable.arrivalTime] = arrivalTime
                it[FlightTable.minPrice] = BigDecimal("100.00")
                it[FlightTable.status] = status
            } get FlightTable.id
        }

    fun createBooking(
        bookingReference: String,
        userId: Int,
        flightId: Int,
        status: BookingStatus = BookingStatus.CONFIRMED,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 5, 1, 10, 0),
    ): Int =
        transaction {
            BookingTable.insert {
                it[BookingTable.bookingReference] = bookingReference
                it[BookingTable.userId] = userId
                it[BookingTable.flightId] = flightId
                it[BookingTable.returnFlightId] = null
                it[BookingTable.status] = status
                it[BookingTable.createdAt] = createdAt
            } get BookingTable.id
        }

    fun createSeat(
        aircraftId: Int,
        seatNumber: String,
        seatClass: SeatClass = SeatClass.ECONOMY,
    ): Int =
        transaction {
            SeatTable.insert {
                it[SeatTable.aircraftId] = aircraftId
                it[SeatTable.seatNumber] = seatNumber
                it[SeatTable.seatClass] = seatClass
            } get SeatTable.id
        }

    fun createFlightSeat(
        flightId: Int,
        seatId: Int,
    ): Int =
        transaction {
            FlightSeatTable.insert {
                it[FlightSeatTable.flightId] = flightId
                it[FlightSeatTable.seatId] = seatId
            } get FlightSeatTable.id
        }

    fun createPassenger(
        bookingId: Int,
        firstname: String = "Test",
        lastname: String = "Passenger",
        passportCode: String? = "P123456",
    ): Int =
        transaction {
            PassengerTable.insert {
                it[PassengerTable.bookingId] = bookingId
                it[PassengerTable.firstname] = firstname
                it[PassengerTable.lastname] = lastname
                it[PassengerTable.passportCode] = passportCode
            } get PassengerTable.id
        }

    fun createTicketAssignment(
        passengerId: Int,
        flightSeatId: Int,
        seatNumber: String,
        ticketPrice: BigDecimal = BigDecimal("120.00"),
    ): Int =
        transaction {
            TicketAssignmentTable.insert {
                it[TicketAssignmentTable.passengerId] = passengerId
                it[TicketAssignmentTable.flightSeatId] = flightSeatId
                it[TicketAssignmentTable.ticketPrice] = ticketPrice
                it[TicketAssignmentTable.seatNumber] = seatNumber
            } get TicketAssignmentTable.id
        }

    fun createPayment(
        bookingId: Int,
        amount: BigDecimal = BigDecimal("150.00"),
        paymentStatus: PaymentStatus = PaymentStatus.COMPLETED,
        paymentMethod: PaymentMethod = PaymentMethod.CARD,
        transactionId: String = "TX-$bookingId",
        createdAt: LocalDateTime = LocalDateTime.of(2026, 5, 1, 12, 0),
    ): Int =
        transaction {
            PaymentTable.insert {
                it[PaymentTable.bookingId] = bookingId
                it[PaymentTable.amount] = amount
                it[PaymentTable.paymentStatus] = paymentStatus
                it[PaymentTable.paymentMethod] = paymentMethod
                it[PaymentTable.transactionId] = transactionId
                it[PaymentTable.createdAt] = createdAt
                it[PaymentTable.refundAmount] = null
                it[PaymentTable.refundDate] = null
            } get PaymentTable.id
        }

    "Update flight status changes status" {
        // Arrange: create a scheduled flight.
        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF100",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        // Act: update the flight status.
        val updatedFlight =
            repository.updateFlightStatus(
                flightId = flightId,
                newStatus = FlightStatus.DELAYED,
            )

        // Assert: the flight should be returned with the new status.
        updatedFlight shouldNotBe null
        updatedFlight!!.id shouldBe flightId
        updatedFlight.status shouldBe FlightStatus.DELAYED
    }

    "Update flight status returns null when flight id does not exist" {
        // Act: try to update a flight that does not exist.
        val result =
            repository.updateFlightStatus(
                flightId = 99999,
                newStatus = FlightStatus.CANCELLED,
            )

        // Assert: the repository should return null instead of crashing.
        result shouldBe null
    }

    "Update flight schedule changes route and creates change log" {
        // Arrange: create an admin user, airports, aircraft, and an existing flight.
        val adminId =
            createTestUser(
                email = "admin-schedule@test.com",
                firstname = "Admin",
                lastname = "Manager",
            )

        val oldDepartureAirportId = createAirport("London Heathrow", "LHR")
        val oldArrivalAirportId = createAirport("Manchester Airport", "MAN")
        val newDepartureAirportId = createAirport("Birmingham Airport", "BHX")
        val newArrivalAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft(type = "Boeing 737")

        val oldDepartureTime = LocalDateTime.of(2026, 5, 1, 9, 0)
        val oldArrivalTime = LocalDateTime.of(2026, 5, 1, 11, 0)

        val flightId =
            createFlight(
                flightCode = "AF200",
                departureAirportId = oldDepartureAirportId,
                arrivalAirportId = oldArrivalAirportId,
                aircraftId = aircraftId,
                departureTime = oldDepartureTime,
                arrivalTime = oldArrivalTime,
            )

        val newDepartureTime = LocalDateTime.of(2026, 6, 1, 14, 30)
        val newArrivalTime = LocalDateTime.of(2026, 6, 1, 16, 30)

        // Act: update the flight route and schedule.
        val updatedFlight =
            repository.updateFlightSchedule(
                flightId = flightId,
                newDepartureAirportId = newDepartureAirportId,
                newArrivalAirportId = newArrivalAirportId,
                newDepartureTime = newDepartureTime,
                newArrivalTime = newArrivalTime,
                changedByUserId = adminId,
            )

        // Assert: the flight should contain the new route and schedule.
        updatedFlight shouldNotBe null
        updatedFlight!!.id shouldBe flightId
        updatedFlight.departureAirportId shouldBe newDepartureAirportId
        updatedFlight.arrivalAirportId shouldBe newArrivalAirportId
        updatedFlight.departureTime shouldBe newDepartureTime
        updatedFlight.arrivalTime shouldBe newArrivalTime

        // Assert: a change log should also be created.
        val changes = repository.getFlightChangesByFlightId(flightId)

        changes.size shouldBe 1
        changes[0].flightId shouldBe flightId
        changes[0].flightCode shouldBe "AF200"
        changes[0].oldDepartureAirportNameCode shouldBe "London Heathrow LHR"
        changes[0].newDepartureAirportNameCode shouldBe "Birmingham Airport BHX"
        changes[0].oldArrivalAirportNameCode shouldBe "Manchester Airport MAN"
        changes[0].newArrivalAirportNameCode shouldBe "Edinburgh Airport EDI"
        changes[0].oldDepartureTime shouldBe oldDepartureTime
        changes[0].newDepartureTime shouldBe newDepartureTime
        changes[0].oldArrivalTime shouldBe oldArrivalTime
        changes[0].newArrivalTime shouldBe newArrivalTime
        changes[0].changedByUserId shouldBe adminId
        changes[0].changedByName shouldBe "Admin Manager"
    }

    "Update flight schedule returns null when flight id does not exist" {
        // Arrange: create the new airports that would have been used.
        val newDepartureAirportId = createAirport("Birmingham Airport", "BHX")
        val newArrivalAirportId = createAirport("Edinburgh Airport", "EDI")

        // Act: try to update a flight that does not exist.
        val result =
            repository.updateFlightSchedule(
                flightId = 99999,
                newDepartureAirportId = newDepartureAirportId,
                newArrivalAirportId = newArrivalAirportId,
                newDepartureTime = LocalDateTime.of(2026, 6, 1, 14, 30),
                newArrivalTime = LocalDateTime.of(2026, 6, 1, 16, 30),
                changedByUserId = null,
            )

        // Assert: no flight should be updated and no change log should be created.
        result shouldBe null
        repository.getAllFlightChanges(
            fromCodes = null,
            toCodes = null,
            date = null,
        ).size shouldBe 0
    }

    "Get all flight changes returns created schedule change records" {
        // Arrange: create one updated flight with a change log.
        val adminId = createTestUser("admin-all-changes@test.com")

        val oldDepartureAirportId = createAirport("London Heathrow", "LHR")
        val oldArrivalAirportId = createAirport("Manchester Airport", "MAN")
        val newDepartureAirportId = createAirport("Birmingham Airport", "BHX")
        val newArrivalAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF300",
                departureAirportId = oldDepartureAirportId,
                arrivalAirportId = oldArrivalAirportId,
                aircraftId = aircraftId,
            )

        repository.updateFlightSchedule(
            flightId = flightId,
            newDepartureAirportId = newDepartureAirportId,
            newArrivalAirportId = newArrivalAirportId,
            newDepartureTime = LocalDateTime.of(2026, 6, 2, 10, 0),
            newArrivalTime = LocalDateTime.of(2026, 6, 2, 12, 0),
            changedByUserId = adminId,
        )

        // Act: retrieve all flight change records.
        val changes =
            repository.getAllFlightChanges(
                fromCodes = null,
                toCodes = null,
                date = null,
            )

        // Assert: the created schedule change should be returned.
        changes.size shouldBe 1
        changes[0].flightId shouldBe flightId
        changes[0].flightCode shouldBe "AF300"
        changes[0].changedByUserId shouldBe adminId
    }

    "Get all flight changes filters by current departure airport and date" {
        // Arrange: create a flight and update it to a new route and date.
        val adminId = createTestUser("admin-filter@test.com")

        val oldDepartureAirportId = createAirport("London Heathrow", "LHR")
        val oldArrivalAirportId = createAirport("Manchester Airport", "MAN")
        val newDepartureAirportId = createAirport("Birmingham Airport", "BHX")
        val newArrivalAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF400",
                departureAirportId = oldDepartureAirportId,
                arrivalAirportId = oldArrivalAirportId,
                aircraftId = aircraftId,
            )

        repository.updateFlightSchedule(
            flightId = flightId,
            newDepartureAirportId = newDepartureAirportId,
            newArrivalAirportId = newArrivalAirportId,
            newDepartureTime = LocalDateTime.of(2026, 7, 5, 8, 0),
            newArrivalTime = LocalDateTime.of(2026, 7, 5, 10, 0),
            changedByUserId = adminId,
        )

        // Act: filter by the current departure airport and date.
        val matchingChanges =
            repository.getAllFlightChanges(
                fromCodes = listOf("BHX"),
                toCodes = null,
                date = LocalDate.of(2026, 7, 5),
            )

        val nonMatchingChanges =
            repository.getAllFlightChanges(
                fromCodes = listOf("LHR"),
                toCodes = null,
                date = LocalDate.of(2026, 7, 5),
            )

        val matchingFlightIds = matchingChanges.map { it.flightId }
        val nonMatchingFlightIds = nonMatchingChanges.map { it.flightId }

        // Assert: the updated flight should match the new current route.
        matchingFlightIds shouldContain flightId
        nonMatchingFlightIds shouldNotContain flightId
    }

    "Get flight changes by flight id returns empty list when no changes exist" {
        // Arrange: create a flight without updating its schedule.
        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF500",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        // Act: retrieve change logs for a flight that has no changes.
        val changes = repository.getFlightChangesByFlightId(flightId)

        // Assert: no change logs should be returned.
        changes.size shouldBe 0
    }

    "Get bookings per flight report counts non-cancelled bookings only" {
        // Arrange: create a user, two flights, and bookings with different statuses.
        val userId = createTestUser("booking-report@test.com")

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val secondArrivalAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft(type = "Airbus A320")

        val firstFlightId =
            createFlight(
                flightCode = "AF600",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        val secondFlightId =
            createFlight(
                flightCode = "AF601",
                departureAirportId = departureAirportId,
                arrivalAirportId = secondArrivalAirportId,
                aircraftId = aircraftId,
            )

        createBooking("BR-AF600-1", userId, firstFlightId, BookingStatus.CONFIRMED)
        createBooking("BR-AF600-2", userId, firstFlightId, BookingStatus.CREATED)
        createBooking("BR-AF600-CANCELLED", userId, firstFlightId, BookingStatus.CANCELLED)
        createBooking("BR-AF601-1", userId, secondFlightId, BookingStatus.CONFIRMED)

        // Act: generate the bookings per flight report.
        val report = repository.getBookingsPerFlightReport()

        val firstFlightReport = report.first { it.flightId == firstFlightId }
        val secondFlightReport = report.first { it.flightId == secondFlightId }

        // Assert: cancelled bookings should not be counted.
        firstFlightReport.flightCode shouldBe "AF600"
        firstFlightReport.departureAirportNameCode shouldBe "London Heathrow LHR"
        firstFlightReport.arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        firstFlightReport.aircraftType shouldBe "Airbus A320"
        firstFlightReport.bookingCount shouldBe 2L

        secondFlightReport.flightCode shouldBe "AF601"
        secondFlightReport.bookingCount shouldBe 1L
    }

    "Dashboard popular routes report orders routes by booking count" {
        // Arrange: create two routes with different numbers of non-cancelled bookings.
        val userId = createTestUser("popular-routes@test.com")

        val londonAirportId = createAirport("London Heathrow", "LHR")
        val manchesterAirportId = createAirport("Manchester Airport", "MAN")
        val edinburghAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft()

        val londonToManchesterFlightId =
            createFlight(
                flightCode = "AF800",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
            )

        val londonToEdinburghFlightId =
            createFlight(
                flightCode = "AF801",
                departureAirportId = londonAirportId,
                arrivalAirportId = edinburghAirportId,
                aircraftId = aircraftId,
            )

        createBooking("BR-POPULAR-1", userId, londonToManchesterFlightId, BookingStatus.CONFIRMED)
        createBooking("BR-POPULAR-2", userId, londonToManchesterFlightId, BookingStatus.CONFIRMED)
        createBooking("BR-POPULAR-CANCELLED", userId, londonToManchesterFlightId, BookingStatus.CANCELLED)
        createBooking("BR-LESS-POPULAR-1", userId, londonToEdinburghFlightId, BookingStatus.CONFIRMED)

        // Act: generate the popular routes report used by the dashboard.
        val report = repository.getMostPopularRoutesReport()

        // Assert: the route with two valid bookings should appear before the route with one valid booking.
        report.size shouldBe 2

        report[0].departureAirportId shouldBe londonAirportId
        report[0].arrivalAirportId shouldBe manchesterAirportId
        report[0].departureAirportNameCode shouldBe "London Heathrow LHR"
        report[0].arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        report[0].bookingCount shouldBe 2L
        report[1].departureAirportId shouldBe londonAirportId
        report[1].arrivalAirportId shouldBe edinburghAirportId
        report[1].departureAirportNameCode shouldBe "London Heathrow LHR"
        report[1].arrivalAirportNameCode shouldBe "Edinburgh Airport EDI"
        report[1].bookingCount shouldBe 1L
    }

    "Dashboard peak booking times report groups bookings by created hour" {
        // Arrange: create bookings at different created hours.
        val userId = createTestUser("peak-booking-times@test.com")

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF900",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        createBooking(
            bookingReference = "BR-PEAK-14-1",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CONFIRMED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 15),
        )

        createBooking(
            bookingReference = "BR-PEAK-14-2",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CONFIRMED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 45),
        )

        createBooking(
            bookingReference = "BR-PEAK-09-1",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CREATED,
            createdAt = LocalDateTime.of(2026, 5, 1, 9, 5),
        )

        createBooking(
            bookingReference = "BR-PEAK-CANCELLED",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CANCELLED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 30),
        )

        // Act: generate the peak booking times report used by the dashboard.
        val report = repository.getPeakBookingTimesReport()

        // Assert: bookings should be grouped by hour, and cancelled bookings should be excluded.
        report[0].bookingHour shouldBe "14:00"
        report[0].bookingCount shouldBe 2L

        val nineOClockReport = report.first { it.bookingHour == "09:00" }
        nineOClockReport.bookingCount shouldBe 1L
    }

    "Get most popular routes report groups bookings by route and orders by booking count" {
        // Arrange: create two routes with different numbers of non-cancelled bookings.
        val userId = createTestUser("popular-routes@test.com")

        val londonAirportId = createAirport("London Heathrow", "LHR")
        val manchesterAirportId = createAirport("Manchester Airport", "MAN")
        val edinburghAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft()

        val londonToManchesterFlightId =
            createFlight(
                flightCode = "AF800",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
            )

        val londonToEdinburghFlightId =
            createFlight(
                flightCode = "AF801",
                departureAirportId = londonAirportId,
                arrivalAirportId = edinburghAirportId,
                aircraftId = aircraftId,
            )

        createBooking("BR-POPULAR-1", userId, londonToManchesterFlightId, BookingStatus.CONFIRMED)
        createBooking("BR-POPULAR-2", userId, londonToManchesterFlightId, BookingStatus.CONFIRMED)
        createBooking("BR-POPULAR-CANCELLED", userId, londonToManchesterFlightId, BookingStatus.CANCELLED)
        createBooking("BR-LESS-POPULAR-1", userId, londonToEdinburghFlightId, BookingStatus.CONFIRMED)

        // Act: generate the most popular routes report.
        val report = repository.getMostPopularRoutesReport()

        // Assert: the route with two valid bookings should appear first.
        report.size shouldBe 2

        report[0].departureAirportId shouldBe londonAirportId
        report[0].arrivalAirportId shouldBe manchesterAirportId
        report[0].departureAirportNameCode shouldBe "London Heathrow LHR"
        report[0].arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        report[0].bookingCount shouldBe 2L

        report[1].departureAirportId shouldBe londonAirportId
        report[1].arrivalAirportId shouldBe edinburghAirportId
        report[1].bookingCount shouldBe 1L
    }

    "Get peak booking times report groups bookings by created hour and excludes cancelled bookings" {
        // Arrange: create bookings at different hours.
        val userId = createTestUser("peak-booking-times@test.com")

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF900",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        createBooking(
            bookingReference = "BR-PEAK-14-1",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CONFIRMED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 15),
        )

        createBooking(
            bookingReference = "BR-PEAK-14-2",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CONFIRMED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 45),
        )

        createBooking(
            bookingReference = "BR-PEAK-09-1",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CREATED,
            createdAt = LocalDateTime.of(2026, 5, 1, 9, 5),
        )

        createBooking(
            bookingReference = "BR-PEAK-CANCELLED",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CANCELLED,
            createdAt = LocalDateTime.of(2026, 5, 1, 14, 30),
        )

        // Act: generate the peak booking times report.
        val report = repository.getPeakBookingTimesReport()

        // Assert: bookings should be grouped by hour and cancelled bookings should be excluded.
        report[0].bookingHour shouldBe "14:00"
        report[0].bookingCount shouldBe 2L

        val nineOClockReport = report.first { it.bookingHour == "09:00" }
        nineOClockReport.bookingCount shouldBe 1L
    }

    "Get flight availability report calculates total booked and available seats" {
        // Arrange: create a flight with three seats and two assigned tickets.
        val userId = createTestUser("availability@test.com", role = UserRole.USER)

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft(type = "Airbus A320", numberOfSeats = 3)

        val departureTime = LocalDateTime.of(2026, 8, 10, 9, 0)

        val flightId =
            createFlight(
                flightCode = "AF1000",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
                departureTime = departureTime,
                arrivalTime = LocalDateTime.of(2026, 8, 10, 11, 0),
            )

        val seatOneId = createSeat(aircraftId, "1A")
        val seatTwoId = createSeat(aircraftId, "1B")
        val seatThreeId = createSeat(aircraftId, "1C")

        val flightSeatOneId = createFlightSeat(flightId, seatOneId)
        val flightSeatTwoId = createFlightSeat(flightId, seatTwoId)
        createFlightSeat(flightId, seatThreeId)

        val firstBookingId =
            createBooking(
                bookingReference = "BR-AVAIL-1",
                userId = userId,
                flightId = flightId,
                status = BookingStatus.CONFIRMED,
            )

        val secondBookingId =
            createBooking(
                bookingReference = "BR-AVAIL-2",
                userId = userId,
                flightId = flightId,
                status = BookingStatus.CONFIRMED,
            )

        val firstPassengerId =
            createPassenger(
                bookingId = firstBookingId,
                firstname = "Alice",
                lastname = "Brown",
            )

        val secondPassengerId =
            createPassenger(
                bookingId = secondBookingId,
                firstname = "Bob",
                lastname = "Smith",
            )

        createTicketAssignment(
            passengerId = firstPassengerId,
            flightSeatId = flightSeatOneId,
            seatNumber = "1A",
        )

        createTicketAssignment(
            passengerId = secondPassengerId,
            flightSeatId = flightSeatTwoId,
            seatNumber = "1B",
        )

        // Act: retrieve availability for the matching route and date.
        val report =
            repository.getFlightAvailabilityReport(
                fromCodes = listOf("LHR"),
                toCodes = listOf("MAN"),
                date = LocalDate.of(2026, 8, 10),
            )

        val summary = report.single()

        // Assert: total seats should be 3, booked seats should be 2, and available seats should be 1.
        summary.flightId shouldBe flightId
        summary.flightCode shouldBe "AF1000"
        summary.departureAirportNameCode shouldBe "London Heathrow LHR"
        summary.arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        summary.aircraftType shouldBe "Airbus A320"
        summary.totalSeats shouldBe 3L
        summary.bookedSeats shouldBe 2L
        summary.availableSeats shouldBe 1L
    }

    "Get cancelled flights returns all cancelled flights and excludes scheduled flights" {
        // Arrange: create cancelled flights on different routes and one scheduled flight.
        val londonAirportId = createAirport("London Heathrow", "LHR")
        val manchesterAirportId = createAirport("Manchester Airport", "MAN")
        val edinburghAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft(type = "Boeing 777")

        val firstCancelledFlightId =
            createFlight(
                flightCode = "AF1200",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 8, 15, 15, 0),
                arrivalTime = LocalDateTime.of(2026, 8, 15, 17, 0),
                status = FlightStatus.CANCELLED,
            )

        val secondCancelledFlightId =
            createFlight(
                flightCode = "AF1202",
                departureAirportId = londonAirportId,
                arrivalAirportId = edinburghAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 8, 16, 15, 0),
                arrivalTime = LocalDateTime.of(2026, 8, 16, 17, 0),
                status = FlightStatus.CANCELLED,
            )

        val scheduledFlightId =
            createFlight(
                flightCode = "AF1201",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 8, 15, 18, 0),
                arrivalTime = LocalDateTime.of(2026, 8, 15, 20, 0),
                status = FlightStatus.SCHEDULED,
            )

        // Act: retrieve all cancelled flights without route or date filters.
        val cancelledFlights =
            repository.getCancelledFlights(
                fromCodes = null,
                toCodes = null,
                date = null,
            )

        val cancelledFlightIds = cancelledFlights.map { it.flightId }

        // Assert: all cancelled flights should be returned, but scheduled flights should be excluded.
        cancelledFlights.size shouldBe 2
        cancelledFlightIds shouldContain firstCancelledFlightId
        cancelledFlightIds shouldContain secondCancelledFlightId
        cancelledFlightIds shouldNotContain scheduledFlightId
    }

    "Get cancelled bookings returns all cancelled bookings and excludes non-cancelled bookings" {
        // Arrange: create cancelled bookings on different routes and one confirmed booking.
        val userId =
            createTestUser(
                email = "cancelled-booking@test.com",
                role = UserRole.USER,
                firstname = "Normal",
                lastname = "User",
            )

        val londonAirportId = createAirport("London Heathrow", "LHR")
        val manchesterAirportId = createAirport("Manchester Airport", "MAN")
        val edinburghAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft(type = "Boeing 737")

        val firstFlightId =
            createFlight(
                flightCode = "AF1100",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 8, 12, 9, 0),
                arrivalTime = LocalDateTime.of(2026, 8, 12, 11, 0),
            )

        val secondFlightId =
            createFlight(
                flightCode = "AF1101",
                departureAirportId = londonAirportId,
                arrivalAirportId = edinburghAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 8, 13, 12, 0),
                arrivalTime = LocalDateTime.of(2026, 8, 13, 14, 0),
            )

        val firstCancelledBookingId =
            createBooking(
                bookingReference = "BR-CANCELLED-1",
                userId = userId,
                flightId = firstFlightId,
                status = BookingStatus.CANCELLED,
                createdAt = LocalDateTime.of(2026, 8, 1, 10, 0),
            )

        val secondCancelledBookingId =
            createBooking(
                bookingReference = "BR-CANCELLED-2",
                userId = userId,
                flightId = secondFlightId,
                status = BookingStatus.CANCELLED,
                createdAt = LocalDateTime.of(2026, 8, 1, 12, 0),
            )

        val confirmedBookingId =
            createBooking(
                bookingReference = "BR-CONFIRMED-1",
                userId = userId,
                flightId = firstFlightId,
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.of(2026, 8, 1, 11, 0),
            )

        // Act: retrieve all cancelled bookings without route or date filters.
        val cancelledBookings =
            repository.getCancelledBookings(
                fromCodes = null,
                toCodes = null,
                date = null,
            )

        val cancelledBookingIds = cancelledBookings.map { it.bookingId }

        // Assert: all cancelled bookings should be returned, but confirmed bookings should be excluded.
        cancelledBookings.size shouldBe 2
        cancelledBookingIds shouldContain firstCancelledBookingId
        cancelledBookingIds shouldContain secondCancelledBookingId
        cancelledBookingIds shouldNotContain confirmedBookingId

        // Assert: the returned summaries should still contain useful customer and flight information.
        val firstSummary = cancelledBookings.first { it.bookingId == firstCancelledBookingId }

        firstSummary.bookingReference shouldBe "BR-CANCELLED-1"
        firstSummary.userId shouldBe userId
        firstSummary.firstname shouldBe "Normal"
        firstSummary.lastname shouldBe "User"
        firstSummary.email shouldBe "cancelled-booking@test.com"
        firstSummary.flightId shouldBe firstFlightId
        firstSummary.flightCode shouldBe "AF1100"
        firstSummary.departureAirportNameCode shouldBe "London Heathrow LHR"
        firstSummary.arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        firstSummary.status shouldBe BookingStatus.CANCELLED
        firstSummary.aircraftType shouldBe "Boeing 737"
    }

    "Get airport id by code normalises lowercase code" {
        // Arrange: create an airport with an uppercase airport code.
        val airportId =
            createAirport(
                name = "London Heathrow",
                code = "LHR",
            )

        // Act: search using a lowercase airport code.
        val result = repository.getAirportIdByCode("lhr")

        // Assert: the repository should normalise the code and return the matching airport ID.
        result shouldBe airportId
    }

    "Get airport id by code returns null when airport code does not exist" {
        // Act: search for an airport code that does not exist.
        val result = repository.getAirportIdByCode("XXX")

        // Assert: the repository should return null instead of crashing.
        result shouldBe null
    }

    "Get all reservations returns reservation summaries with customer and flight details" {
        // Arrange: create one reservation with payment information.
        val userId =
            createTestUser(
                email = "reservation-user@test.com",
                role = UserRole.USER,
                firstname = "Alice",
                lastname = "Brown",
            )

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft(type = "Airbus A320")

        val departureTime = LocalDateTime.of(2026, 9, 10, 9, 0)

        val flightId =
            createFlight(
                flightCode = "AF1300",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
                departureTime = departureTime,
                arrivalTime = LocalDateTime.of(2026, 9, 10, 11, 0),
            )

        val bookingId =
            createBooking(
                bookingReference = "BR-RES-1",
                userId = userId,
                flightId = flightId,
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
            )

        createPayment(
            bookingId = bookingId,
            amount = BigDecimal("199.99"),
            transactionId = "TX-RES-1",
        )

        // Act: retrieve all reservations without filters.
        val reservations =
            repository.getAllReservations(
                fromCodes = null,
                toCodes = null,
                date = null,
                status = null,
            )

        val summary = reservations.single()

        // Assert: the reservation summary should contain the details needed by the admin reservations views.
        summary.bookingId shouldBe bookingId
        summary.bookingReference shouldBe "BR-RES-1"
        summary.userId shouldBe userId
        summary.firstname shouldBe "Alice"
        summary.lastname shouldBe "Brown"
        summary.email shouldBe "reservation-user@test.com"
        summary.flightId shouldBe flightId
        summary.flightCode shouldBe "AF1300"
        summary.departureAirportNameCode shouldBe "London Heathrow LHR"
        summary.arrivalAirportNameCode shouldBe "Manchester Airport MAN"
        summary.departureTime shouldBe departureTime
        summary.bookingStatus shouldBe BookingStatus.CONFIRMED
        summary.amountPaid shouldBe BigDecimal("199.99")
        summary.aircraftType shouldBe "Airbus A320"
    }

    "Get all reservations filters by route date and booking status" {
        // Arrange: create reservations that match and do not match the selected filters.
        val userId =
            createTestUser(
                email = "reservation-filter@test.com",
                role = UserRole.USER,
                firstname = "Filter",
                lastname = "User",
            )

        val londonAirportId = createAirport("London Heathrow", "LHR")
        val manchesterAirportId = createAirport("Manchester Airport", "MAN")
        val edinburghAirportId = createAirport("Edinburgh Airport", "EDI")
        val aircraftId = createAircraft(type = "Boeing 737")

        val matchingFlightId =
            createFlight(
                flightCode = "AF1400",
                departureAirportId = londonAirportId,
                arrivalAirportId = manchesterAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 9, 15, 9, 0),
                arrivalTime = LocalDateTime.of(2026, 9, 15, 11, 0),
            )

        val wrongRouteFlightId =
            createFlight(
                flightCode = "AF1401",
                departureAirportId = londonAirportId,
                arrivalAirportId = edinburghAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 9, 15, 9, 0),
                arrivalTime = LocalDateTime.of(2026, 9, 15, 11, 0),
            )

        val matchingBookingId =
            createBooking(
                bookingReference = "BR-FILTER-MATCH",
                userId = userId,
                flightId = matchingFlightId,
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
            )

        val wrongStatusBookingId =
            createBooking(
                bookingReference = "BR-FILTER-CANCELLED",
                userId = userId,
                flightId = matchingFlightId,
                status = BookingStatus.CANCELLED,
                createdAt = LocalDateTime.of(2026, 9, 1, 11, 0),
            )

        val wrongRouteBookingId =
            createBooking(
                bookingReference = "BR-FILTER-WRONG-ROUTE",
                userId = userId,
                flightId = wrongRouteFlightId,
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.of(2026, 9, 1, 12, 0),
            )

        // Act: filter reservations by route, departure date, and booking status.
        val reservations =
            repository.getAllReservations(
                fromCodes = listOf("LHR"),
                toCodes = listOf("MAN"),
                date = LocalDate.of(2026, 9, 15),
                status = BookingStatus.CONFIRMED,
            )

        val reservationIds = reservations.map { it.bookingId }

        // Assert: only the booking matching all filters should be returned.
        reservations.size shouldBe 1
        reservationIds shouldContain matchingBookingId
        reservationIds shouldNotContain wrongStatusBookingId
        reservationIds shouldNotContain wrongRouteBookingId
    }

    "Search reservations by customer returns empty list when query is blank" {
        // Arrange: create a reservation that should not be returned for a blank query.
        val userId =
            createTestUser(
                email = "blank-search@test.com",
                role = UserRole.USER,
                firstname = "Blank",
                lastname = "Search",
            )

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft()

        val flightId =
            createFlight(
                flightCode = "AF1500",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        createBooking(
            bookingReference = "BR-BLANK-SEARCH",
            userId = userId,
            flightId = flightId,
            status = BookingStatus.CONFIRMED,
        )

        // Act: search using only whitespace.
        val results = repository.searchReservationsByCustomer("   ")

        // Assert: blank search should return an empty list.
        results.size shouldBe 0
    }

    "Search reservations by customer matches email booking reference and full name" {
        // Arrange: create reservations for two different users.
        val firstUserId =
            createTestUser(
                email = "john.wick@test.com",
                role = UserRole.USER,
                firstname = "John",
                lastname = "Wick",
            )

        val secondUserId =
            createTestUser(
                email = "jane.smith@test.com",
                role = UserRole.USER,
                firstname = "Jane",
                lastname = "Smith",
            )

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Manchester Airport", "MAN")
        val aircraftId = createAircraft(type = "Airbus A321")

        val firstFlightId =
            createFlight(
                flightCode = "AF1600",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        val secondFlightId =
            createFlight(
                flightCode = "AF1601",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
                departureTime = LocalDateTime.of(2026, 9, 20, 12, 0),
                arrivalTime = LocalDateTime.of(2026, 9, 20, 14, 0),
            )

        val firstBookingId =
            createBooking(
                bookingReference = "BR-JOHN-1",
                userId = firstUserId,
                flightId = firstFlightId,
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
            )

        val secondBookingId =
            createBooking(
                bookingReference = "BR-JANE-1",
                userId = secondUserId,
                flightId = secondFlightId,
                status = BookingStatus.CREATED,
                createdAt = LocalDateTime.of(2026, 9, 1, 11, 0),
            )

        // Act: search by email, booking reference, and reversed full name order.
        val emailResults = repository.searchReservationsByCustomer("john.wick@test.com")
        val bookingReferenceResults = repository.searchReservationsByCustomer("BR-JANE-1")
        val reversedFullNameResults = repository.searchReservationsByCustomer("Wick John")

        // Assert: email search should find John's booking.
        emailResults.map { it.bookingId } shouldContain firstBookingId

        // Assert: booking reference search should find Jane's booking.
        bookingReferenceResults.map { it.bookingId } shouldContain secondBookingId

        // Assert: reversed full name search should still find John's booking.
        reversedFullNameResults.map { it.bookingId } shouldContain firstBookingId
    }
})
