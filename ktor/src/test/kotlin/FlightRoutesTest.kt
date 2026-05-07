import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.routes.flightRoutes
import com.flightbooking.sessions.UserSession
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
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

fun Application.testFlightApplication() {
    install(ContentNegotiation) {
        jackson()
    }

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
        }
    }

    install(Pebble) {
        loader(
            io.pebbletemplates.pebble.loader.ClasspathLoader().also {
                it.prefix = "templates"
            },
        )
    }

    routing {
        flightRoutes()
    }
}

fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
        application { testFlightApplication() }
        block()
    }

class FlightRoutesTest : StringSpec({

    var databaseFile: Path? = null
    var airportCounter = 0
    var flightCounter = 0

    beforeTest {
        airportCounter = 0
        flightCounter = 0
        databaseFile = Files.createTempFile("flight-routes-test", ".sqlite")

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

    fun createTestAircraft(): Int =
        transaction {
            AircraftTable.insert {
                it[type] = "Boeing 737"
                it[numOfSeats] = 100
            } get AircraftTable.id
        }

    fun createTestFlight(
        departureAirportId: Int,
        arrivalAirportId: Int,
        departureTime: LocalDateTime = LocalDateTime.now().plusDays(7).withHour(10),
        arrivalTime: LocalDateTime = LocalDateTime.now().plusDays(7).withHour(12),
        minPrice: BigDecimal = BigDecimal("100.00"),
        flightCode: String = "FL%05d".format(flightCounter++),
    ): Int =
        transaction {
            val aircraft = createTestAircraft()
            FlightTable.insert {
                it[FlightTable.flightCode] = flightCode
                it[FlightTable.departureAirportId] = departureAirportId
                it[FlightTable.arrivalAirportId] = arrivalAirportId
                it[aircraftId] = aircraft
                it[FlightTable.departureTime] = departureTime
                it[FlightTable.arrivalTime] = arrivalTime
                it[FlightTable.minPrice] = minPrice
                it[status] = FlightStatus.SCHEDULED
            } get FlightTable.id
        }

    fun createTestFlightSeat(
        flightId: Int,
        seatNumber: String = "10A",
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
                    it[SeatTable.seatClass] = SeatClass.ECONOMY
                } get SeatTable.id
            FlightSeatTable.insert {
                it[FlightSeatTable.flightId] = flightId
                it[FlightSeatTable.seatId] = seatId
            } get FlightSeatTable.id
        }

    "GET /search returns 200 and renders flightsearch template" {
        val depId = createTestAirport(code = "LBA")
        val arrId = createTestAirport(code = "LHR")
        val flightId = createTestFlight(depId, arrId, departureTime = LocalDateTime.now().plusDays(1).withHour(10))
        createTestFlightSeat(flightId)

        withApp {
            val response = client.get("/search?from=LBA&departure_date=${LocalDate.now().plusDays(1)}&num_of_passengers=1")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LBA"
        }
    }

    "GET /search with no matching flights still returns 200" {
        withApp {
            val response = client.get("/search?from=XYZ&departure_date=${LocalDate.now().plusDays(1)}")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    "GET /search with blank from param falls back to LBA default" {
        val depId = createTestAirport(code = "LBA")
        val arrId = createTestAirport(code = "LHR")
        val flightId = createTestFlight(depId, arrId, departureTime = LocalDateTime.now().withHour(10))
        createTestFlightSeat(flightId)

        withApp {
            val response = client.get("/search?from=&departure_date=${LocalDate.now()}")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    "GET /search with invalid departure_date renders error" {
        withApp {
            val response = client.get("/search?from=LBA&departure_date=not-a-date")
            response.status shouldBe HttpStatusCode.OK

            response.bodyAsText() shouldContain "<main"
            response.bodyAsText() shouldContain "search"
        }
    }

    "GET /search with invalid return_date renders error" {
        withApp {
            val response = client.get("/search?from=LBA&departure_date=${LocalDate.now().plusDays(1)}&return_date=bad")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "<main"
            response.bodyAsText() shouldContain "search"
        }
    }

    "GET /search with valid return_date renders returnsearch template" {
        val depId = createTestAirport(code = "LBA")
        val arrId = createTestAirport(code = "LHR")
        val outboundDate = LocalDate.now().plusDays(1)
        val returnDate = LocalDate.now().plusDays(5)
        val outboundFlight = createTestFlight(depId, arrId, departureTime = outboundDate.atTime(10, 0))
        val returnFlight = createTestFlight(arrId, depId, departureTime = returnDate.atTime(14, 0))
        createTestFlightSeat(outboundFlight)
        createTestFlightSeat(returnFlight)

        withApp {
            val response =
                client.get(
                    "/search?from=LBA&to=LHR&departure_date=$outboundDate&return_date=$returnDate&num_of_passengers=1",
                )
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LBA"
            response.bodyAsText() shouldContain "LHR"
        }
    }

    "GET /airports returns matching airports as JSON" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response = client.get("/airports?search=Leeds")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LBA"
            response.bodyAsText() shouldContain "Leeds Bradford Airport"
        }
    }

    "GET /airports returns empty list when search is shorter than 2 characters" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response = client.get("/airports?search=L")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    "GET /airports returns empty list when search param is missing" {
        withApp {
            val response = client.get("/airports")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    "GET /airports returns empty list when no airports match" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response = client.get("/airports?search=XYZ")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    "GET /airports matches on code prefix" {
        createTestAirport(code = "LHR", name = "Heathrow Airport")
        createTestAirport(code = "MAN", name = "Manchester Airport")

        withApp {
            val response = client.get("/airports?search=LH")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LHR"
            response.bodyAsText() shouldNotContain "MAN"
        }
    }

    "GET /airports/search returns 400 when field param is missing" {
        withApp {
            val response = client.get("/airports/search?search=Leeds")
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "Missing field parameter"
        }
    }

    "GET /airports/search returns 400 when field param is not from or to" {
        withApp {
            val response = client.get("/airports/search?search=Leeds&field=invalid")
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "Incorrect field parameter"
        }
    }

    "GET /airports/search renders index template with empty list when search is too short" {
        withApp {
            val response = client.get("/airports/search?search=L&field=from")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "<main"
            response.bodyAsText() shouldContain "search"
        }
    }

    "GET /airports/search with field=from renders matching airports in index template" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response = client.get("/airports/search?search=Leeds&field=from")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LBA"
        }
    }

    "GET /airports/search with field=to renders matching airports in index template" {
        createTestAirport(code = "LHR", name = "Heathrow Airport")

        withApp {
            val response = client.get("/airports/search?search=Heath&field=to")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LHR"
        }
    }

    "GET /airports/search preserves existing form values in template model" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response =
                client.get(
                    "/airports/search?search=Leeds&field=from&from=LBA&to=LHR&date=2025-06-01&numOfPassengers=2",
                )
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "LBA"
            response.bodyAsText() shouldContain "LHR"
        }
    }

    "GET /airports/search returns empty results when no airports match search" {
        createTestAirport(code = "LBA", name = "Leeds Bradford Airport")

        withApp {
            val response = client.get("/airports/search?search=XYZ&field=from")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "<main"
            response.bodyAsText() shouldContain "search"
        }
    }
})
