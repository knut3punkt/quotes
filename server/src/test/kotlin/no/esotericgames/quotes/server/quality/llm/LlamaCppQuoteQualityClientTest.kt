package no.esotericgames.quotes.server.quality.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.quality.QualityLlmConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TEST_CONFIG = QualityLlmConfig("http://localhost:8888", "test-model", 0.1, 900, 5000)
private val REQUEST = QualityRequest("system", "QUOTATION\n\nText.", GenerationSettings("test-model", 0.1, 900))
private const val VALID_CONTENT =
    """{\"reading\":\"r\",\"unresolvedReferences\":[],\"insight\":\"i\",\"problems\":[\"FLAT\"],\"standsAlone\":5,""" +
        """\"completeness\":5,\"substance\":3,\"expression\":3,\"assessment\":\"a\",\"verdict\":\"acceptable\"}"""

class LlamaCppQuoteQualityClientTest {

    @Test
    fun `a schema-conforming response maps to Success`() = runBlocking<Unit> {
        val success = assertIs<QualityOutcome.Success>(clientResponding(VALID_CONTENT).assess(REQUEST))

        assertEquals("test-model", success.modelId)
        assertEquals("acceptable", success.response.verdict)
        assertEquals(listOf("FLAT"), success.response.problems)
    }

    @Test
    fun `a truncated response maps to MalformedResponse even when it parses`() = runBlocking<Unit> {
        val result = assertIs<QualityOutcome.MalformedResponse>(clientResponding(VALID_CONTENT, finishReason = "length").assess(REQUEST))

        assertTrue(result.message.contains("truncated"))
    }

    @Test
    fun `content missing the verdict maps to MalformedResponse`() = runBlocking<Unit> {
        assertIs<QualityOutcome.MalformedResponse>(clientResponding("""{\"reading\":\"r\"}""").assess(REQUEST))
    }

    @Test
    fun `a connection exception maps to ConnectionFailure`() = runBlocking<Unit> {
        val client = LlamaCppQuoteQualityClient(TEST_CONFIG, testHttpClient(MockEngine { throw java.net.ConnectException("refused") }))

        assertIs<QualityOutcome.ConnectionFailure>(client.assess(REQUEST))
    }

    @Test
    fun `the request carries the schema-constrained response format`() = runBlocking<Unit> {
        var body = ""
        val client = LlamaCppQuoteQualityClient(
            TEST_CONFIG,
            testHttpClient(
                MockEngine { request ->
                    body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    respond(envelope(VALID_CONTENT, null), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        )

        client.assess(REQUEST)

        assertTrue("\"quote_quality_assessment\"" in body)
        assertTrue("\"max_tokens\":900" in body)
    }

    private fun envelope(escapedContent: String, finishReason: String?): String {
        val finish = finishReason?.let { "\"finish_reason\":\"$it\"," } ?: ""
        return """{"model":"test-model","choices":[{$finish"message":{"role":"assistant","content":"$escapedContent"}}]}"""
    }

    private fun clientResponding(escapedContent: String, finishReason: String? = null): LlamaCppQuoteQualityClient {
        val mockEngine = MockEngine {
            respond(envelope(escapedContent, finishReason), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LlamaCppQuoteQualityClient(TEST_CONFIG, testHttpClient(mockEngine))
    }

    private fun testHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
    }
}
