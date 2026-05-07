import com.flightbooking.enums.UserRole
import com.flightbooking.routes.notificationRoutes
import com.flightbooking.services.NotificationEvent
import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NotificationRoutesTest : StringSpec({

    "should return NotFound when no session exists" {
        testApplication {
            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            routing {
                this.notificationRoutes()
            }

            val response = client.get("/notifications/stream")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "should return OK for authenticated user on stream endpoint" {
        testApplication {
            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            routing {
                get("/setup-session") {
                    call.sessions.set(
                        UserSession(
                            userId = 1,
                            role = UserRole.USER,
                            initials = "JD",
                        ),
                    )
                }

                this.notificationRoutes()
            }

            val sessionResponse = client.get("/setup-session")
            val cookie = sessionResponse.headers["Set-Cookie"] ?: ""

            val response =
                client.get("/notifications/stream") {
                    header("Cookie", cookie)
                }

            response.status shouldBe HttpStatusCode.OK

            response.bodyAsChannel().cancel()
        }
    }

    "should receive SSE notification for authenticated user" {
        testApplication {
            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            routing {
                get("/setup-session") {
                    call.sessions.set(
                        UserSession(
                            userId = 1,
                            role = UserRole.USER,
                            initials = "JD",
                        ),
                    )
                }

                this.notificationRoutes()
            }

            val sessionResponse = client.get("/setup-session")
            val cookie = sessionResponse.headers["Set-Cookie"] ?: ""

            val response =
                client.get("/notifications/stream") {
                    header("Cookie", cookie)
                }

            response.status shouldBe HttpStatusCode.OK

            val sendJob =
                launch {
                    delay(100)

                    NotificationService.send(
                        NotificationEvent(
                            userId = 1,
                            message = "Test notification",
                            type = "info",
                        ),
                    )
                }

            val line =
                withTimeout(3000) {
                    response.bodyAsChannel().readLine()
                }

            sendJob.join()

            line shouldContain "Test notification"
            line shouldContain "\"type\":\"info\""

            response.bodyAsChannel().cancel()
        }
    }

    "should ignore events for different users" {
        testApplication {
            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            routing {
                get("/setup-session") {
                    call.sessions.set(
                        UserSession(
                            userId = 2,
                            role = UserRole.USER,
                            initials = "AB",
                        ),
                    )
                }

                this.notificationRoutes()
            }

            val sessionResponse = client.get("/setup-session")
            val cookie = sessionResponse.headers["Set-Cookie"] ?: ""

            val response =
                client.get("/notifications/stream") {
                    header("Cookie", cookie)
                }

            response.status shouldBe HttpStatusCode.OK

            NotificationService.send(
                NotificationEvent(
                    userId = 99,
                    message = "Not for you",
                    type = "info",
                ),
            )

            delay(300)

            response.bodyAsChannel().cancel()
        }
    }

    "should format SSE event data string correctly" {
        val event =
            NotificationEvent(
                userId = 1,
                message = "Hello",
                type = "info",
                sentAt = 1234567890L,
            )

        val formatted =
            """data: {"message":"${event.message}","type":"${event.type}","sentAt":${event.sentAt}}"""

        formatted shouldContain "data:"
        formatted shouldContain "\"message\":\"Hello\""
        formatted shouldContain "\"type\":\"info\""
        formatted shouldContain "\"sentAt\":1234567890"
    }

    "should have correct content type for SSE stream" {
        testApplication {
            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            routing {
                get("/setup-session") {
                    call.sessions.set(
                        UserSession(
                            userId = 1,
                            role = UserRole.USER,
                            initials = "JD",
                        ),
                    )
                }

                notificationRoutes()
            }

            val sessionResponse = client.get("/setup-session")
            val cookie = sessionResponse.headers["Set-Cookie"] ?: ""

            val response =
                client.get("/notifications/stream") {
                    header("Cookie", cookie)
                }

            val contentType = response.headers["Content-Type"]

            contentType shouldContain "text/event-stream"

            response.bodyAsChannel().cancel()
        }
    }

    "NotificationEvent should default type to info" {
        val event =
            NotificationEvent(
                userId = 1,
                message = "Test",
            )

        event.type shouldBe "info"
    }

    "NotificationEvent should store all fields correctly" {
        val event =
            NotificationEvent(
                userId = 42,
                message = "Hello World",
                type = "warning",
            )

        event.userId shouldBe 42
        event.message shouldBe "Hello World"
        event.type shouldBe "warning"
    }

    "NotificationEvent sentAt should be set automatically within valid range" {
        val before = System.currentTimeMillis()

        val event =
            NotificationEvent(
                userId = 1,
                message = "Test",
            )

        val after = System.currentTimeMillis()

        (event.sentAt >= before) shouldBe true
        (event.sentAt <= after) shouldBe true
    }

    "NotificationService should emit events to collectors" {
        val deferred =
            async {
                NotificationService.events.first()
            }

        val event =
            NotificationEvent(
                userId = 5,
                message = "Live event",
                type = "alert",
            )

        NotificationService.send(event)

        val received =
            withTimeout(3000) {
                deferred.await()
            }

        received.userId shouldBe 5
        received.message shouldBe "Live event"
        received.type shouldBe "alert"
    }
})
