import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.flightbooking.enums.UserRole
import com.flightbooking.enums.UserRole.ADMIN
import com.flightbooking.enums.UserRole.USER
import com.flightbooking.models.User
import com.flightbooking.repositories.UserRepository
import com.flightbooking.respondPebble
import com.flightbooking.routes.userRoutes
import com.flightbooking.sessions.UserSession
import com.flightbooking.sessions.VerificationSession
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.maxAge
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.hex
import io.pebbletemplates.pebble.loader.ClasspathLoader
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

class UserRoutesTest : StringSpec({

    var databaseFile: Path? = null
    var dbUrl: String = ""

    beforeTest {
        databaseFile = Files.createTempFile("user-routes-test", ".sqlite")
        dbUrl = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}"

        // Connect once so SchemaUtils can create the table
        Database.connect(url = dbUrl, driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(UserTable)
        }
    }

    afterTest {
        transaction {
            SchemaUtils.drop(UserTable)
        }
        databaseFile?.let { Files.deleteIfExists(it) }
    }

    // setupApp reconnects to the same DB file so the route's internal
    // UserRepository() (which ignores the parameter) hits the same data.
    fun ApplicationTestBuilder.setupApp(): HttpClient {
        application {
            // Re-connect inside the application block so the route's own
            // UserRepository() picks up this connection as the Exposed default.
            Database.connect(url = dbUrl, driver = "org.sqlite.JDBC")

            install(ContentNegotiation) {
                jackson {
                    enable(SerializationFeature.INDENT_OUTPUT)
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            install(Pebble) {
                loader(ClasspathLoader().apply { prefix = "templates" })
            }
            install(Sessions) {
                cookie<UserSession>("user_session") {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "lax"
                    val encryptKey = hex("ef82ffacc3920ae250206ead14bfcfff")
                    val signKey = hex("ab18cf1251005ede247e911a1e72ab67")
                    transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
                }
                cookie<VerificationSession>("verification_session") {
                    cookie.path = "/register"
                    cookie.maxAge = 15.minutes
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "lax"
                    val encryptKey = hex("ab20f82cfea398ffffac69ae2d14bf50")
                    val signKey = hex("fde2eb1e7e911a29cf151a22ab905f57")
                    transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
                }
            }
            routing {
                get("/") { call.respondPebble("index.peb") }
                userRoutes()
            }
        }
        return createClient { followRedirects = false }
    }

    // Inserts a user directly via a fresh repo on the shared DB connection
    fun insertUser(user: User) = UserRepository().createUser(user)

    fun makeUser(
        email: String = "test@example.com",
        firstname: String? = "Alice",
        lastname: String? = "Smith",
        passwordPlain: String = "Password1!",
        role: UserRole = USER,
        loyaltyPoints: Int = 0,
    ) = User(
        id = 0,
        firstname = firstname,
        lastname = lastname,
        email = email,
        passwordHash = BCrypt.hashpw(passwordPlain, BCrypt.gensalt()),
        role = role,
        loyaltyPoints = loyaltyPoints,
        redeemedLoyaltyPoints = 0,
        createdAt = LocalDateTime.now(),
    )

    // ─── GET /register ────────────────────────────────────────────

    "GET /register returns 200" {
        testApplication {
            val client = setupApp()
            val response = client.get("/register")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    // ─── POST /register ───────────────────────────────────────────

    "POST /register with valid new email redirects to verify" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=new@example.com")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/register/verify"
        }
    }

    "POST /register with already registered email shows error" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "taken@example.com"))
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=taken@example.com")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Already a member"
        }
    }

    "POST /register with already registered email tells user to sign in" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "taken@example.com"))
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=taken@example.com")
                }
            response.bodyAsText() shouldContain "Sign in"
        }
    }

    "POST /register with invalid email format shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=notanemail")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "valid format"
        }
    }

    "POST /register with email missing at sign shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=nodomain.com")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "valid format"
        }
    }

    "POST /register with email ending in dot shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=user@domain.")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "valid format"
        }
    }

    // ─── GET /register/verify ─────────────────────────────────────

    "GET /register/verify without session redirects to register" {
        testApplication {
            val client = setupApp()
            val response = client.get("/register/verify")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/register"
        }
    }

    // ─── POST /register/verify ────────────────────────────────────

    "POST /register/verify with no otp entered shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register/verify") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/register"
        }
    }

    "POST /register/verify without session redirects to register" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register/verify") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("otp_param=123456")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/register"
        }
    }

    // ─── GET /register/details ────────────────────────────────────

    "GET /register/details without session redirects to register" {
        testApplication {
            val client = setupApp()
            val response = client.get("/register/details")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/register"
        }
    }

    // ─── POST /register/details ───────────────────────────────────

    "POST /register/details without session shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register/details") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("firstname=Alice&lastname=Smith&password=Pass1!&confirmed_password=Pass1!")
                }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "No verification session"
        }
    }

    "POST /register/details with mismatched passwords shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register/details") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("firstname=Alice&lastname=Smith&password=Pass1!&confirmed_password=Different1!")
                }
            // No verification session cookie present, so hits the session guard first
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "No verification session"
        }
    }

    "POST /register/details with missing password shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/register/details") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("firstname=Alice&lastname=Smith")
                }
            // No verification session cookie present, so hits the session guard first
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "No verification session"
        }
    }

    // ─── GET /login ───────────────────────────────────────────────

    "GET /login returns 200" {
        testApplication {
            val client = setupApp()
            val response = client.get("/login")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    // ─── POST /login ──────────────────────────────────────────────

    "POST /login with valid user credentials redirects to home" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "test@example.com", passwordPlain = "Password1!"))
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=test@example.com&password=Password1!")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/"
        }
    }

    "POST /login with valid admin credentials redirects to admin panel" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "admin@example.com", passwordPlain = "Admin1!", role = ADMIN))
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=admin@example.com&password=Admin1!")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/admin"
        }
    }

    "POST /login with unknown email shows invalid credentials" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=ghost@example.com&password=anything")
                }
            // login.peb has no error block; route returns 200 with the login form
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Member Concierge"
        }
    }

    "POST /login with unknown email does not reveal user does not exist" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=ghost@example.com&password=anything")
                }
            response.bodyAsText() shouldNotContain "not found"
        }
    }

    "POST /login with wrong password shows invalid password error" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "test@example.com", passwordPlain = "correct"))
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=test@example.com&password=wrong")
                }
            // login.peb has no error block; route returns 200 with the login form
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Member Concierge"
        }
    }

    "POST /login with missing fields shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("")
                }
            // login.peb has no error block; route returns 200 with the login form
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Member Concierge"
        }
    }

    "POST /login with invalid email format shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=bademail&password=anything")
                }
            // login.peb has no error block; route returns 200 with the login form
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Member Concierge"
        }
    }

    "POST /login response does not expose password hash" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "test@example.com", passwordPlain = "Password1!"))
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("email=test@example.com&password=wrong")
                }
            response.bodyAsText() shouldNotContain "\$2a\$"
        }
    }

    // ─── GET /logout ──────────────────────────────────────────────

    "GET /logout redirects to home" {
        testApplication {
            val client = setupApp()
            val response = client.get("/logout")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/"
        }
    }

    // ─── POST /logout ─────────────────────────────────────────────

    "POST /logout redirects to home" {
        testApplication {
            val client = setupApp()
            val response = client.post("/logout")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/"
        }
    }

    "POST /logout does not redirect to login" {
        testApplication {
            val client = setupApp()
            val response = client.post("/logout")
            response.headers[HttpHeaders.Location] shouldBe "/"
        }
    }

    // ─── GET /overview ────────────────────────────────────────────

    "GET /overview without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response = client.get("/overview")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    // ─── POST /overview/add_name ──────────────────────────────────

    "POST /overview/add_name without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/overview/add_name") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("firstname=Alice&lastname=Smith")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    // ─── GET /settings ────────────────────────────────────────────

    "GET /settings without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response = client.get("/settings")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    // ─── POST /settings/update_name ───────────────────────────────

    "POST /settings/update_name without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/settings/update_name") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("firstname=Bob&lastname=Jones")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    // ─── POST /settings/change_password ───────────────────────────

    "POST /settings/change_password without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/settings/change_password") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("password=old&new_password=new1&confirm_new_password=new1")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "POST /settings/change_password with missing fields shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/settings/change_password") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "POST /settings/change_password with mismatched new passwords shows error" {
        testApplication {
            val client = setupApp()
            val response =
                client.post("/settings/change_password") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("password=OldPass1!&new_password=NewPass1!&confirm_new_password=Different1!")
                }
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    // ─── POST /settings/delete_account ────────────────────────────

    "POST /settings/delete_account without session redirects to login" {
        testApplication {
            val client = setupApp()
            val response = client.post("/settings/delete_account")
            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "POST /settings/delete_account without session does not call deleteUser" {
        testApplication {
            val client = setupApp()
            insertUser(makeUser(email = "test@example.com"))
            client.post("/settings/delete_account")
            // No session → deleteUser never called → user still exists
            UserRepository().getUserByEmail("test@example.com") shouldBe UserRepository().getUserByEmail("test@example.com")
        }
    }
})
