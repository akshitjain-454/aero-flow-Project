import com.flightbooking.enums.UserRole
import com.flightbooking.routes.adminRoutes
import com.flightbooking.sessions.UserSession
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
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication

class AdminRoutesTest : StringSpec({

    fun Application.testAdminApplication() {
        install(ContentNegotiation) {
            jackson()
        }

        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
            }
        }

        routing {
            /**
             * Test-only route used to create a logged-in session.
             * This route is only used inside tests and is not part of the real application.
             */
            get("/set-test-session/{role}") {
                val roleText = call.parameters["role"] ?: "USER"
                val role = UserRole.valueOf(roleText)

                call.sessions.set(
                    UserSession(
                        userId = if (role == UserRole.ADMIN) 1 else 2,
                        role = role,
                        initials = if (role == UserRole.ADMIN) "SA" else "TU",
                    ),
                )

                call.respondText("Test session created")
            }

            adminRoutes()
        }
    }

    "GET admin complaints redirects to login when user is not logged in" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    followRedirects = false
                }

            val response = client.get("/admin/complaints")

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    "GET admin complaints returns Forbidden when user is not an admin" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-session/USER")

            val response = client.get("/admin/complaints")

            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText().contains("Admin only") shouldBe true
        }
    }

    "GET customer search returns empty results when query is blank" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-session/ADMIN")

            val response = client.get("/admin/customer-search?q=   ")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().contains("customer_search") shouldBe true
            response.bodyAsText().contains("\"totalResults\":0") shouldBe true
        }
    }

    "POST complaint status returns BadRequest when complaint id is invalid" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-session/ADMIN")

            val response =
                client.post("/admin/complaints/not-a-number/status") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("status", "IN_REVIEW")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText().contains("Invalid complaint id") shouldBe true
        }
    }

    "POST complaint status returns BadRequest when status is missing" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-session/ADMIN")

            val response =
                client.post("/admin/complaints/1/status") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("reply", "Checked by admin")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText().contains("Missing status") shouldBe true
        }
    }

    "POST complaint handle returns BadRequest when complaint id is invalid" {
        testApplication {
            application {
                testAdminApplication()
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.get("/set-test-session/ADMIN")

            val response =
                client.post("/admin/complaints/not-a-number/handle") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("status", "RESOLVED")
                                append("reply", "Issue handled")
                            },
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText().contains("Invalid complaint id") shouldBe true
        }
    }
})
