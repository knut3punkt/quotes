package no.esotericgames.quotes.server.extraction.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TEST_CONFIG = ExtractionLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "test-model",
    temperature = 0.1,
    maxOutputTokens = 500,
    requestTimeoutMillis = 5000,
)

private fun requestFor(generation: GenerationSettings = GenerationSettings("test-model", 0.1, 500)) =
    ExcerptSelectionRequest(systemPrompt = "system", userContent = "user", generation = generation)

class LlamaCppExcerptSelectionClientTest {

    @Test
    fun `a schema-conforming response maps to Success with candidates and model id`() = runBlocking {
        val client = clientRespondingWith {
            """
            {
              "model": "test-model",
              "choices": [
                { "message": { "role": "assistant", "content": "{\"excerpts\":[{\"startUnit\":1,\"endUnit\":2,\"independence\":90,\"completeness\":90,\"quotability\":80,\"contextualFidelity\":95,\"reason\":\"clear\"}]}" } }
              ]
            }
            """.trimIndent()
        }

        val outcome = client.selectExcerpts(requestFor())

        val success = assertIs<InferenceOutcome.Success>(outcome)
        assertEquals("test-model", success.modelId)
        assertEquals(1, success.candidates.size)
        assertEquals(1, success.candidates[0].startUnit)
        assertEquals(2, success.candidates[0].endUnit)
    }

    @Test
    fun `an empty excerpts array maps to a Success with no candidates`() = runBlocking {
        val client = clientRespondingWith {
            """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"{\"excerpts\":[]}"}}]}"""
        }

        val outcome = client.selectExcerpts(requestFor())

        val success = assertIs<InferenceOutcome.Success>(outcome)
        assertTrue(success.candidates.isEmpty())
    }

    @Test
    fun `a non-2xx status maps to MalformedResponse`() = runBlocking {
        val mockEngine = MockEngine { respond(content = "server error", status = HttpStatusCode.InternalServerError) }
        val client = LlamaCppExcerptSelectionClient(TEST_CONFIG, testHttpClient(mockEngine))

        val outcome = client.selectExcerpts(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `an undecodable envelope maps to MalformedResponse`() = runBlocking {
        val client = clientRespondingWith { "not json at all" }

        val outcome = client.selectExcerpts(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `content that fails to decode against the excerpt schema maps to MalformedResponse`() = runBlocking {
        val client = clientRespondingWith {
            """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"not the expected json shape"}}]}"""
        }

        val outcome = client.selectExcerpts(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `a connection exception maps to ConnectionFailure`() = runBlocking {
        val mockEngine = MockEngine { throw java.net.ConnectException("connection refused") }
        val client = LlamaCppExcerptSelectionClient(TEST_CONFIG, testHttpClient(mockEngine))

        val outcome = client.selectExcerpts(requestFor())

        val result = assertIs<InferenceOutcome.ConnectionFailure>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `the outgoing request includes the schema-constrained response_format and generation settings`() = runBlocking {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = ((request.body) as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"{\"excerpts\":[]}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LlamaCppExcerptSelectionClient(TEST_CONFIG, testHttpClient(mockEngine))

        client.selectExcerpts(requestFor(GenerationSettings("test-model", 0.1, 500, reasoningEffort = "low")))

        val body = requireNotNull(capturedBody)
        assertTrue(body.contains("\"response_format\""))
        assertTrue(body.contains("\"json_schema\""))
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("\"reasoning_effort\":\"low\""))
    }

    private fun testHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
    }

    private fun clientRespondingWith(body: () -> String): LlamaCppExcerptSelectionClient {
        val mockEngine = MockEngine {
            respond(
                content = body(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return LlamaCppExcerptSelectionClient(TEST_CONFIG, testHttpClient(mockEngine))
    }
}
