import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.BookingRepository
import com.flightbooking.routes.bookingRoutes
import com.flightbooking.sessions.UserSession
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class BookingRoutesTest : StringSpec({

    lateinit var databaseFile: Path

    beforeTest {
        // Create a temporary SQLite database file for isolation
        databaseFile = Files.createTempFile("booking-routes-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        // Added ALL dependent tables to prevent Foreign Key crashes
        transaction {
            SchemaUtils.create(UserTable, AirportTable, AircraftTable, FlightTable, BookingTable)
        }
    }

    afterTest {
        // Drop tables in reverse order of dependencies
        transaction {
            SchemaUtils.drop(BookingTable, FlightTable, AircraftTable, AirportTable, UserTable)
        }
        Files.deleteIfExists(databaseFile)
    }

    /** Helper: Create Test User */
    fun createTestUser(
        email: String,
        role: UserRole = UserRole.USER,
        firstname: String = "Test",
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

    /** Helper: Create Test Airport */

    fun createTestAirport(codeStr: String): Int =
        transaction {
            AirportTable.insert {
                it[name] = "Test Airport"
                it[code] = codeStr
                it[city] = "Test City"
                it[country] = "Test Country"
            } get AirportTable.id
        }

    /** Helper: Create Test Aircraft */

    fun createTestAircraft(): Int =
        transaction {
            AircraftTable.insert {
                it[type] = "Gulfstream G650"
                it[numOfSeats] = 14
            } get AircraftTable.id
        }

    /** Helper: Create Test Flight */

    fun createTestFlight(
        flightCodeStr: String,
        depAirportId: Int,
        arrAirportId: Int,
        airId: Int,
    ): Int =
        transaction {
            FlightTable.insert {
                it[flightCode] = flightCodeStr
                it[departureAirportId] = depAirportId
                it[arrivalAirportId] = arrAirportId
                it[aircraftId] = airId
                it[departureTime] = LocalDateTime.now().plusDays(5)
                it[arrivalTime] = LocalDateTime.now().plusDays(5).plusHours(4)
                it[minPrice] = BigDecimal("5000.00")
                it[status] = FlightStatus.SCHEDULED
            } get FlightTable.id
        }

    /** Helper: Configure Ktor App */

    fun Application.testBookingApplication() {
        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
            }
        }

        routing {
            get("/set-test-user-session/{userId}") {
                val userId = call.parameters["userId"]!!.toInt()
                call.sessions.set(
                    UserSession(
                        userId = userId,
                        role = UserRole.USER,
                        initials = "TU",
                    ),
                )
                call.respondText("Test session created")
            }

            bookingRoutes()
        }
    }

    "POST booking create_booking redirects to login when user is not logged in" {
        testApplication {
            application {
                testBookingApplication()
            }

            val client =
                createClient {
                    followRedirects = false
                }

            val response =
                client.post("/booking/create_booking") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("flight_code", "LHR-NYC-123")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "POST booking create_booking returns NotFound for invalid flight code" {
        val userId = createTestUser("booker@test.com")

        testApplication {
            application {
                testBookingApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-user-session/$userId")

            val response =
                client.post("/booking/create_booking") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("flight_code", "INVALID-CODE")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldBe "Flight not found"
        }
    }

    "POST booking create_booking creates booking and redirects to passengers page" {
        val userId = createTestUser("valid-booking@test.com")
        val flightCode = "LHR-NYC-100"

        // Satisfy Foreign Key Constraints
        val lhrId = createTestAirport("LHR")
        val nycId = createTestAirport("NYC")
        val aircraftId = createTestAircraft()

        createTestFlight(flightCode, lhrId, nycId, aircraftId)

        testApplication {
            application {
                testBookingApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-user-session/$userId")

            val response =
                client.post("/booking/create_booking") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("flight_code", flightCode)
                            },
                        ),
                    )
                }

            // 1. Check HTTP Routing Behavior
            response.status shouldBe HttpStatusCode.Found
            val location = response.headers[HttpHeaders.Location]
            location?.shouldStartWith("/booking/")
            location?.shouldEndWith("/passengers")

            // 2. Check actual Database state
            val bookings = BookingRepository().getBookingsByUserId(userId)

            bookings.size shouldBe 1
            bookings[0].status shouldBe BookingStatus.CREATED
            bookings[0].userId shouldBe userId
        }
    }
})
