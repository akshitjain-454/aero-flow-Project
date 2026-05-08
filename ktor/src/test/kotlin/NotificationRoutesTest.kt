import com.flightbooking.enums.UserRole
import com.flightbooking.routes.notificationRoutes
import com.flightbooking.services.NotificationEvent
import com.flightbooking.services.NotificationService
import com.flightbooking.sessions.UserSession
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeoutOrNull

class NotificationRoutesTest : StringSpec({

    val testSession = UserSession(userId = 1, role = UserRole.USER, initials = "AB")

    fun ApplicationTestBuilder.configureApp(session: UserSession? = null) {
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

    // Reads lines from the SSE stream until the predicate is satisfied or timeout elapses
    suspend fun HttpResponse.readSseUntil(
        timeoutMs: Long = 1000,
        predicate: (String) -> Boolean,
    ): String {
        val collected = StringBuilder()
        withTimeoutOrNull(timeoutMs) {
            val channel = bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                collected.appendLine(line)
                if (predicate(collected.toString())) break
            }
        }
        return collected.toString()
    }

    "returns empty response with no SSE data when no session exists" {
        testApplication {
            configureApp(session = null)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            val body = withTimeoutOrNull(300) { client.get("/notifications/stream").bodyAsText() }

            withClue("Unauthenticated request should receive no SSE data") {
                (body == null || body.isEmpty()).shouldBeTrue()
            }
        }
    }

    "returns 200 with SSE content type when session exists" {
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            client.prepareGet("/notifications/stream").execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.contentType()?.withoutParameters() shouldBe ContentType.Text.EventStream
            }
        }
    }

    "sets Cache-Control no-cache header" {
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            client.prepareGet("/notifications/stream").execute { response ->
                withClue("Expected Cache-Control: no-cache header") {
                    (response.headers[HttpHeaders.CacheControl] ?: "").contains("no-cache").shouldBeTrue()
                }
            }
        }
    }

    "sets Transfer-Encoding chunked header" {
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "hello"))

            client.prepareGet("/notifications/stream").execute { response ->
                response.headers[HttpHeaders.TransferEncoding] shouldBe "chunked"
            }
        }
    }

    "emits correctly formatted SSE data for matching userId" {
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

            client.prepareGet("/notifications/stream").execute { response ->
                val body = response.readSseUntil { it.contains("Gate changed to B7") }

                withClue("body=$body") {
                    body.contains("\"message\":\"Gate changed to B7\"").shouldBeTrue()
                    body.contains("\"type\":\"gate_change\"").shouldBeTrue()
                    body.contains("\"sentAt\":1700001234").shouldBeTrue()
                    body.trimStart().startsWith("data: {").shouldBeTrue()
                    body.contains("}\n").shouldBeTrue()
                }
            }
        }
    }

    "does not emit events belonging to a different userId" {
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 2, message = "Secret"))

            client.prepareGet("/notifications/stream").execute { response ->
                val body = response.readSseUntil(timeoutMs = 300) { it.contains("Secret") }

                withClue("Stream should not forward events for a different user") {
                    body.contains("Secret").shouldBeFalse()
                }
            }
        }
    }

    "only the most recent event is replayed due to replay cache of 1" {
        testApplication {
            configureApp(session = testSession)

            NotificationService.send(NotificationEvent(userId = 1, message = "First"))
            NotificationService.send(NotificationEvent(userId = 1, message = "Second"))
            NotificationService.send(NotificationEvent(userId = 1, message = "Third"))

            client.prepareGet("/notifications/stream").execute { response ->
                val body = response.readSseUntil { it.contains("Third") }

                withClue("Only the last replayed event should appear, body=$body") {
                    body.contains("Third").shouldBeTrue()
                    body.contains("First").shouldBeFalse()
                    body.contains("Second").shouldBeFalse()
                }
            }
        }
    }

    "mixed-user events only delivers events for the session user" {
        testApplication {
            configureApp(session = testSession)

            // Send user 1's event last so it is the replayed entry (replay = 1)
            NotificationService.send(NotificationEvent(userId = 2, message = "For user 2"))
            NotificationService.send(NotificationEvent(userId = 1, message = "For user 1"))

            client.prepareGet("/notifications/stream").execute { response ->
                val body = response.readSseUntil { it.contains("For user 1") }

                withClue("body=$body") {
                    body.contains("For user 1").shouldBeTrue()
                    body.contains("For user 2").shouldBeFalse()
                }
            }
        }
    }
})
