import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.BookingRepository
import com.flightbooking.repositories.FlightRepository
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

class FlightRepositoryTest : StringSpec({
    lateinit var repository: FlightRepository
    var databaseFile: Path? = null
    var airportCounter = 0
    var flightCounter = 0

    beforeTest {
        airportCounter = 0
        flightCounter = 0
        databaseFile = Files.createTempFile("flight-repository-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        transaction {
            SchemaUtils.create(
                UserTable,
                AirportTable,
                AircraftTable,
                SeatTable,
                FlightTable,
                FlightSeatTable,
                BookingTable,
                PassengerTable,
                TicketAssignmentTable,
                PaymentTable,
            )
        }

        repository = FlightRepository()
    }

    afterTest {
        transaction {
            SchemaUtils.drop(
                PaymentTable,
                TicketAssignmentTable,
                PassengerTable,
                BookingTable,
                FlightSeatTable,
                SeatTable,
                FlightTable,
                AircraftTable,
                AirportTable,
                UserTable,
            )
        }
        databaseFile?.let { Files.deleteIfExists(it) }
    }

    fun createTestAirport(
        code: String? = null,
        name: String = "Test Airport",
        city: String = "Test City",
        country: String = "Test Country",
    ): Int =
        transaction {
            AirportTable.insert {
                it[AirportTable.code] = code ?: "A%02d".format(airportCounter++)
                it[AirportTable.name] = name
                it[AirportTable.city] = city
                it[AirportTable.country] = country
            } get AirportTable.id
        }

    fun createTestAircraft(numOfSeats: Int = 100): Int =
        transaction {
            AircraftTable.insert {
                it[type] = "Boeing 737"
                it[AircraftTable.numOfSeats] = numOfSeats
            } get AircraftTable.id
        }

    fun createTestFlight(
        departureAirportId: Int? = null,
        arrivalAirportId: Int? = null,
        departureTime: LocalDateTime = LocalDateTime.now().plusDays(7),
        arrivalTime: LocalDateTime = LocalDateTime.now().plusDays(7).plusHours(2),
        minPrice: BigDecimal = BigDecimal("100.00"),
        status: FlightStatus = FlightStatus.SCHEDULED,
    ): Int =
        transaction {
            val dep = departureAirportId ?: createTestAirport()
            val arr = arrivalAirportId ?: createTestAirport()
            val aircraft = createTestAircraft()
            FlightTable.insert {
                it[flightCode] = "FL%05d".format(flightCounter++)
                it[FlightTable.departureAirportId] = dep
                it[FlightTable.arrivalAirportId] = arr
                it[aircraftId] = aircraft
                it[FlightTable.departureTime] = departureTime
                it[FlightTable.arrivalTime] = arrivalTime
                it[FlightTable.minPrice] = minPrice
                it[FlightTable.status] = status
            } get FlightTable.id
        }

    fun createTestFlightSeat(
        flightId: Int,
        seatNumber: String = "10A",
        seatClass: SeatClass = SeatClass.ECONOMY,
    ): Int =
        transaction {
            val aircraftId =
                FlightTable
                    .select { FlightTable.id eq flightId }
                    .single()[FlightTable.aircraftId]
            val seatId =
                SeatTable.insert {
                    it[SeatTable.aircraftId] = aircraftId
                    it[SeatTable.seatNumber] = seatNumber
                    it[SeatTable.seatClass] = seatClass
                } get SeatTable.id
            FlightSeatTable.insert {
                it[FlightSeatTable.flightId] = flightId
                it[FlightSeatTable.seatId] = seatId
            } get FlightSeatTable.id
        }

    "Search flights returns matching flight for given departure code and date" {
        val depId = createTestAirport(code = "LBA")
        createTestFlight(
            departureAirportId = depId,
            departureTime = LocalDateTime.now().plusDays(1).withHour(10),
            arrivalTime = LocalDateTime.now().plusDays(1).withHour(12),
        ).also { createTestFlightSeat(it) }

        val results =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )

        results shouldHaveSize 1
        results.single().departureAirportCode shouldBe "LBA"
    }

    "Search flights defaults to LBA when fromCodes is null" {
        val depId = createTestAirport(code = "LBA")
        createTestFlight(
            departureAirportId = depId,
            departureTime = LocalDateTime.now().withHour(10),
            arrivalTime = LocalDateTime.now().withHour(12),
        ).also { createTestFlightSeat(it) }

        val results =
            repository.searchFlights(
                fromCodes = null,
                toCodes = null,
                date = LocalDate.now(),
                numOfPassengers = null,
                departureFlexibility = null,
            )

        results shouldHaveSize 1
        results.single().departureAirportCode shouldBe "LBA"
    }

    "Search flights defaults to today when date is null" {
        val depId = createTestAirport(code = "LBA")
        createTestFlight(
            departureAirportId = depId,
            departureTime = LocalDateTime.now().withHour(15),
            arrivalTime = LocalDateTime.now().withHour(17),
        ).also { createTestFlightSeat(it) }

        val results =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = null,
                numOfPassengers = 1,
                departureFlexibility = null,
            )

        results shouldHaveSize 1
    }

    "Search flights filters by arrival airport code" {
        val depId = createTestAirport(code = "LBA")
        val arrMatchId = createTestAirport(code = "LHR")
        val arrOtherId = createTestAirport(code = "MAN")

        createTestFlight(
            departureAirportId = depId,
            arrivalAirportId = arrMatchId,
            departureTime = LocalDateTime.now().plusDays(1).withHour(10),
            arrivalTime = LocalDateTime.now().plusDays(1).withHour(12),
        ).also { createTestFlightSeat(it) }

        createTestFlight(
            departureAirportId = depId,
            arrivalAirportId = arrOtherId,
            departureTime = LocalDateTime.now().plusDays(1).withHour(10),
            arrivalTime = LocalDateTime.now().plusDays(1).withHour(12),
        ).also { createTestFlightSeat(it) }

        val results =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = listOf("LHR"),
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )

        results shouldHaveSize 1
        results.single().arrivalAirportCode shouldBe "LHR"
    }

    "Search flights excludes fully booked flights" {
        val bookingRepository = BookingRepository()

        val depId = createTestAirport(code = "LBA")
        val userId =
            transaction {
                UserTable.insert {
                    it[email] = "test@example.com"
                    it[firstname] = "Test"
                    it[lastname] = "User"
                    it[passwordHash] = "hash"
                    it[role] = UserRole.USER
                    it[loyaltyPoints] = 0
                    it[redeemedLoyaltyPoints] = 0
                    it[createdAt] = LocalDateTime.now()
                } get UserTable.id
            }

        val flightId =
            createTestFlight(
                departureAirportId = depId,
                departureTime = LocalDateTime.now().plusDays(1).withHour(10),
                arrivalTime = LocalDateTime.now().plusDays(1).withHour(12),
            )
        val flightSeatId = createTestFlightSeat(flightId, "1A")

        // Occupy the only seat
        val booking = bookingRepository.createBooking(userId, flightId, null)
        val passenger = bookingRepository.addPassenger(booking.id, "John", "Doe", null)
        bookingRepository.ticketAssignment(passenger.id, flightSeatId, BigDecimal("100.00"), "1A")

        val results =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )

        results.shouldBeEmpty()
    }

    "Search flights with flexibility widens the date window" {
        val depId = createTestAirport(code = "LBA")

        createTestFlight(
            departureAirportId = depId,
            departureTime = LocalDateTime.now().plusDays(3).withHour(10),
            arrivalTime = LocalDateTime.now().plusDays(3).withHour(12),
        ).also { createTestFlightSeat(it) }

        val resultsNoFlex =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(5),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )
        val resultsWith2DayFlex =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(5),
                numOfPassengers = 1,
                departureFlexibility = 2,
            )

        resultsNoFlex.shouldBeEmpty()
        resultsWith2DayFlex shouldHaveSize 1
    }

    "Search flights requires enough available seats for all passengers" {
        val depId = createTestAirport(code = "LBA")
        val flightId =
            createTestFlight(
                departureAirportId = depId,
                departureTime = LocalDateTime.now().plusDays(1).withHour(10),
                arrivalTime = LocalDateTime.now().plusDays(1).withHour(12),
            )
        createTestFlightSeat(flightId, "1A")

        val resultsOne =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )
        val resultsTwo =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = null,
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 2,
                departureFlexibility = 0,
            )

        resultsOne shouldHaveSize 1
        resultsTwo.shouldBeEmpty()
    }

    "Search flights returns empty when no flights match departure code" {
        createTestAirport(code = "LBA")
        createTestFlight().also { createTestFlightSeat(it) }

        val results =
            repository.searchFlights(
                fromCodes = listOf("XYZ"),
                toCodes = null,
                date = LocalDate.now().plusDays(7),
                numOfPassengers = 1,
                departureFlexibility = 0,
            )

        results.shouldBeEmpty()
    }

    "Search flights maps FlightInfo fields correctly" {
        val depId = createTestAirport(code = "LBA", name = "Leeds Bradford Airport", city = "Leeds", country = "UK")
        val arrId = createTestAirport(code = "LHR", name = "Heathrow Airport", city = "London", country = "UK")
        val depTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)

        createTestFlight(
            departureAirportId = depId,
            arrivalAirportId = arrId,
            departureTime = depTime,
            minPrice = BigDecimal("149.99"),
        ).also { createTestFlightSeat(it) }

        val result =
            repository.searchFlights(
                fromCodes = listOf("LBA"),
                toCodes = listOf("LHR"),
                date = LocalDate.now().plusDays(1),
                numOfPassengers = 1,
                departureFlexibility = 0,
            ).single()

        result.departureAirport shouldBe "Leeds Bradford Airport"
        result.departureAirportCode shouldBe "LBA"
        result.arrivalAirport shouldBe "Heathrow Airport"
        result.arrivalAirportCode shouldBe "LHR"
        result.departureTime shouldBe depTime
        result.priceFrom shouldBe BigDecimal("149.99")
    }

    "Get flight by flight code returns correct flight" {
        val flightId = createTestFlight()
        val flightCode =
            transaction {
                FlightTable.select { FlightTable.id eq flightId }.single()[FlightTable.flightCode]
            }

        val result = repository.getFlightByFlightCode(flightCode)

        result.shouldNotBeNull()
        result.id shouldBe flightId
        result.flightCode shouldBe flightCode
    }

    "Get flight by flight code returns null for unknown code" {
        val result = repository.getFlightByFlightCode("UNKNOWN")

        result.shouldBeNull()
    }

    "Get flight by flight id returns correct flight" {
        val flightId = createTestFlight()

        val result = repository.getFlightByFlightId(flightId)

        result.shouldNotBeNull()
        result.id shouldBe flightId
    }

    "Get flight by flight id returns null for unknown id" {
        val result = repository.getFlightByFlightId(9999)

        result.shouldBeNull()
    }

    "Get airport by id returns correct airport" {
        val airportId = createTestAirport(code = "LBA", name = "Leeds Bradford Airport", city = "Leeds", country = "UK")

        val result = repository.getAirportById(airportId)

        result.id shouldBe airportId
        result.code shouldBe "LBA"
        result.name shouldBe "Leeds Bradford Airport"
        result.city shouldBe "Leeds"
        result.country shouldBe "UK"
    }

    "Get airport by id throws when airport does not exist" {
        val exception = runCatching { repository.getAirportById(9999) }.exceptionOrNull()

        exception.shouldNotBeNull()
        exception shouldBe instanceOf<IllegalStateException>()
    }

    "Get airport by search matches on name prefix" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")
        createTestAirport(code = "MAN", name = "Manchester Airport")

        val results = repository.getAirportBySearch("Leeds")

        results shouldHaveSize 1
        results.single().code shouldBe "LBA"
    }

    "Get airport by search matches on code prefix" {
        createTestAirport(code = "LHR", name = "Heathrow Airport")
        createTestAirport(code = "LGW", name = "Gatwick Airport")
        createTestAirport(code = "MAN", name = "Manchester Airport")

        val results = repository.getAirportBySearch("LH")

        results shouldHaveSize 1
        results.single().code shouldBe "LHR"
    }

    "Get airport by search matches on city prefix" {
        createTestAirport(code = "LBA", city = "Leeds")
        createTestAirport(code = "MAN", city = "Manchester")

        val results = repository.getAirportBySearch("Leeds")

        results shouldHaveSize 1
        results.single().code shouldBe "LBA"
    }

    "Get airport by search matches on country prefix" {
        createTestAirport(code = "CDG", country = "France")
        createTestAirport(code = "LHR", country = "United Kingdom")

        val results = repository.getAirportBySearch("Fra")

        results shouldHaveSize 1
        results.single().code shouldBe "CDG"
    }

    "Get airport by search returns up to 10 results" {
        repeat(15) { i ->
            createTestAirport(code = "Z%02d".format(i), name = "Zeta Airport $i")
        }

        val results = repository.getAirportBySearch("Zeta")

        results shouldHaveSize 10
    }

    "Get airport by search returns empty list when nothing matches" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        val results = repository.getAirportBySearch("XYZ")

        results.shouldBeEmpty()
    }

    "Get airport by search is case-sensitive to the LIKE operator behaviour" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        val lower = repository.getAirportBySearch("leeds")

        lower shouldHaveSize 1
    }
})
