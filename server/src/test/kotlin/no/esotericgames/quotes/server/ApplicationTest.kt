package no.esotericgames.quotes.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health endpoint returns 200 with ok status`() = testApplication {
        application { module() }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(HealthResponse(status = "ok"), response.body<HealthResponse>())
    }

    @Test
    fun `quotes endpoint returns sample quotes as json`() = testApplication {
        application { module() }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
        }

        val response = client.get("/api/quotes")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val quotes = response.body<List<Quote>>()
        assertTrue(quotes.size in 2..3, "expected 2 or 3 sample quotes, got ${quotes.size}")
        assertTrue(quotes.all { it.text.isNotBlank() && it.author.isNotBlank() })
    }
}
