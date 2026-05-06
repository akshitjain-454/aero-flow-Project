import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.AdminRepository
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightChangeLogTable
import com.flightbooking.tables.FlightTable
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

    "Get bookings per flight by flight code normalises lowercase code and excludes cancelled bookings" {
        // Arrange: create a flight with confirmed and cancelled bookings.
        val userId = createTestUser("single-flight-report@test.com")

        val departureAirportId = createAirport("London Heathrow", "LHR")
        val arrivalAirportId = createAirport("Birmingham Airport", "BHX")
        val aircraftId = createAircraft(type = "Boeing 737")

        val flightId =
            createFlight(
                flightCode = "AF700",
                departureAirportId = departureAirportId,
                arrivalAirportId = arrivalAirportId,
                aircraftId = aircraftId,
            )

        createBooking("BR-AF700-1", userId, flightId, BookingStatus.CONFIRMED)
        createBooking("BR-AF700-2", userId, flightId, BookingStatus.CONFIRMED)
        createBooking("BR-AF700-CANCELLED", userId, flightId, BookingStatus.CANCELLED)

        // Act: search using lowercase flight code.
        val report = repository.getBookingsPerFlightByFlightCode("af700")

        // Assert: the repository should uppercase the code and exclude cancelled bookings.
        report shouldNotBe null
        report!!.flightId shouldBe flightId
        report.flightCode shouldBe "AF700"
        report.departureAirportNameCode shouldBe "London Heathrow LHR"
        report.arrivalAirportNameCode shouldBe "Birmingham Airport BHX"
        report.aircraftType shouldBe "Boeing 737"
        report.bookingCount shouldBe 2L
    }

    "Get bookings per flight by flight code returns null when flight does not exist" {
        // Act: search for a flight code that does not exist.
        val report = repository.getBookingsPerFlightByFlightCode("UNKNOWN")

        // Assert: the repository should return null instead of crashing.
        report shouldBe null
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
})
