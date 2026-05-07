package com.flightbooking.routes

import com.flightbooking.enums.UserRole
import com.flightbooking.services.NotificationEvent
import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class NotificationRoutesTest {
    private val testSession = UserSession(userId = 1, role = UserRole.USER, initials = "AB")
    private val otherSession = UserSession(userId = 2, role = UserRole.USER, initials = "CD")

    @BeforeEach
    fun setUp() {
        NotificationService.reset()
    }

    private fun ApplicationTestBuilder.configureApp(session: UserSession? = null) {
        install(Sessions) {
            cookie<UserSession>("user_session")
        }
        if (session != null) {
            application {
                intercept(ApplicationCallPipeline.Plugins) {
                    call.sessions.set(session)
                    proceed()
                }
            }
        }
        routing {
            notificationRoutes()
        }
    }

    @Test
    fun `returns 404 when no session exists`() =
        testApplication {
            configureApp(session = null)

            val response = client.get("/notifications/stream")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("Not Found", response.bodyAsText())
        }

    @Test
    fun `returns 200 with SSE content type when session exists`() =
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            val response = client.get("/notifications/stream")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.EventStream, response.contentType()?.withoutParameters())
        }

    @Test
    fun `sets Cache-Control no-cache header`() =
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            val response = client.get("/notifications/stream")

            assertTrue(
                (response.headers[HttpHeaders.CacheControl] ?: "").contains("no-cache"),
                "Expected Cache-Control: no-cache header",
            )
        }

    @Test
    fun `sets Transfer-Encoding chunked header`() =
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            val response = client.get("/notifications/stream")

            assertEquals("chunked", response.headers[HttpHeaders.TransferEncoding])
        }

    @Test
    fun `emits correctly formatted SSE data for matching userId`() =
        testApplication {
            configureApp(session = testSession)

            val event =
                NotificationEvent(
                    userId = 1,
                    message = "Gate changed to B7",
                    type = "gate_change",
                    sentAt = 1700001234L,
                )
            NotificationService.send(event)

            val body = client.get("/notifications/stream").bodyAsText()

            assertTrue(body.contains("\"message\":\"Gate changed to B7\""), "body=$body")
            assertTrue(body.contains("\"type\":\"gate_change\""), "body=$body")
            assertTrue(body.contains("\"sentAt\":1700001234"), "body=$body")
            assertTrue(body.startsWith("data: {"), "SSE line must start with 'data: ', body=$body")
            assertTrue(body.contains("}\n\n"), "SSE line must end with double newline, body=$body")
        }

    @Test
    fun `does not emit events belonging to a different userId`() =
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 2, message = "Secret"))

            val body =
                withTimeoutOrNull(300) {
                    runBlocking { client.get("/notifications/stream").bodyAsText() }
                }

            assertTrue(
                body == null || !body.contains("Secret"),
                "Stream should not forward events for a different user",
            )
        }

    @Test
    fun `emits multiple events for the same user in order`() =
        testApplication {
            configureApp(session = testSession)

            val messages = listOf("First", "Second", "Third")

            val body =
                coroutineScope {
                    val job =
                        launch(Dispatchers.IO) {
                            messages.forEach { msg ->
                                NotificationService.send(NotificationEvent(userId = 1, message = msg))
                                delay(10)
                            }
                        }
                    val response = client.get("/notifications/stream").bodyAsText()
                    job.cancelAndJoin()
                    response
                }

            val indices = messages.map { body.indexOf(it) }.filter { it >= 0 }
            assertEquals(indices.sorted(), indices, "Events should appear in emission order, body=$body")
        }

    @Test
    fun `mixed-user events only delivers events for the session user`() =
        testApplication {
            configureApp(session = testSession) // session userId = 1

            // userId 1 event goes into replay cache; userId 2 is filtered
            NotificationService.send(NotificationEvent(userId = 2, message = "For user 2"))
            NotificationService.send(NotificationEvent(userId = 1, message = "For user 1"))

            val body = client.get("/notifications/stream").bodyAsText()

            assertTrue(body.contains("For user 1"), "Expected user 1 message, body=$body")
            assertFalse(body.contains("For user 2"), "Should not contain user 2 message, body=$body")
        }
}
