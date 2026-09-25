package no.esotericgames.quotes.server.sources.wikiquote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikiquoteClientTest {

    @Test
    fun `resolveTitle follows redirects to the canonical page title`() = runBlocking {
        val client = clientRespondingWith { """{"query":{"pages":[{"title":"Albert Einstein"}]}}""" }

        assertEquals("Albert Einstein", client.resolveTitle("Einstein"))
    }

    @Test
    fun `resolveTitle returns null for a missing page`() = runBlocking {
        val client = clientRespondingWith {
            """{"query":{"pages":[{"title":"Nonexistent Person","missing":true}]}}"""
        }

        assertNull(client.resolveTitle("Nonexistent Person"))
    }

    @Test
    fun `listTopLevelSections only returns toc-level-1 entries`() = runBlocking {
        val client = clientRespondingWith {
            """
            {"parse":{"tocdata":{"sections":[
                {"tocLevel":1,"line":"Quotes","index":"1"},
                {"tocLevel":2,"line":"1890s","index":"2"},
                {"tocLevel":1,"line":"Disputed","index":"5"}
            ]}}}
            """.trimIndent()
        }

        val sections = client.listTopLevelSections("Albert Einstein")

        assertEquals(listOf("Quotes", "Disputed"), sections.map { it.line })
    }

    @Test
    fun `fetchSectionHtml returns the revision id and raw html`() = runBlocking {
        val client = clientRespondingWith { """{"parse":{"revid":123456,"text":"<ul><li>Hi</li></ul>"}}""" }

        val content = client.fetchSectionHtml("Albert Einstein", "1")

        assertEquals(123456L, content.revisionId)
        assertEquals("<ul><li>Hi</li></ul>", content.html)
    }

    private fun clientRespondingWith(body: (HttpRequestData) -> String): WikiquoteClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = body(request),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return WikiquoteClient(httpClient)
    }
}
