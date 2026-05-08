import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.routes.complaintRoutes
import com.flightbooking.sessions.UserSession
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ComplaintRoutesTest : StringSpec({

    lateinit var databaseFile: Path

    beforeTest {
        databaseFile = Files.createTempFile("complaint-routes-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        transaction {
            SchemaUtils.create(UserTable, ComplaintTable)
        }
    }

    afterTest {
        transaction {
            SchemaUtils.drop(ComplaintTable, UserTable)
        }

        Files.deleteIfExists(databaseFile)
    }

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

    fun Application.testComplaintApplication() {
        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
            }
        }

        routing {
            /**
             * Test-only route used to create a logged-in session.
             * This is not part of the real application.
             */
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

            complaintRoutes()
        }
    }

    "POST complaints submit redirects to login when user is not logged in" {
        testApplication {
            application {
                testComplaintApplication()
            }

            val client =
                createClient {
                    followRedirects = false
                }

            val response =
                client.post("/complaints/submit") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("message", "My flight was delayed")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "POST complaints submit returns BadRequest when message is blank" {
        val userId = createTestUser("blank-message@test.com")

        testApplication {
            application {
                testComplaintApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-user-session/$userId")

            val response =
                client.post("/complaints/submit") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("message", "   ")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe "Complaint message is required"
        }
    }

    "POST complaints submit creates complaint and redirects to complaints page" {
        val userId = createTestUser("valid-complaint@test.com")

        testApplication {
            application {
                testComplaintApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-user-session/$userId")

            val response =
                client.post("/complaints/submit") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("message", "The booking page crashed")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/complaints"

            val complaints = ComplaintRepository().getComplaintsByUserId(userId)

            complaints.size shouldBe 1
            complaints[0].message shouldBe "The booking page crashed"
            complaints[0].status shouldBe ComplaintStatus.OPEN
        }
    }
})
