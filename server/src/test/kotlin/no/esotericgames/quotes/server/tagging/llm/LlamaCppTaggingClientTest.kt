package no.esotericgames.quotes.server.tagging.llm

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
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.tagging.TaggingLlmConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TEST_CONFIG = TaggingLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "test-model",
    temperature = 0.5,
    maxOutputTokens = 500,
    requestTimeoutMillis = 5000,
)

private fun requestFor(generation: GenerationSettings = GenerationSettings("test-model", 0.5, 500)) =
    TaggingRequest(systemPrompt = "system", userContent = "user", generation = generation)

class LlamaCppTaggingClientTest {

    @Test
    fun `a schema-conforming response maps to Success with tags per facet and model id`() = runBlocking {
        val client = clientRespondingWith {
            """
            {
              "model": "test-model",
              "choices": [
                { "message": { "role": "assistant", "content": "{\"concepts\":[{\"name\":\"death\",\"breadth\":\"broad\",\"relevance\":3,\"basis\":\"text\"}],\"moods\":[{\"name\":\"serene\",\"relevance\":2,\"basis\":\"text\"}],\"motifs\":[]}" } }
              ]
            }
            """.trimIndent()
        }

        val outcome = client.generateTags(requestFor())

        val success = assertIs<InferenceOutcome.Success>(outcome)
        assertEquals("test-model", success.modelId)
        assertEquals(1, success.tagging.concepts.size)
        assertEquals("death", success.tagging.concepts[0].name)
        assertEquals("broad", success.tagging.concepts[0].breadth)
        assertEquals(null, success.tagging.moods[0].breadth)
    }

    @Test
    fun `empty tag arrays map to a Success with no tags`() = runBlocking {
        val client = clientRespondingWith {
            """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"{\"concepts\":[],\"moods\":[],\"motifs\":[]}"}}]}"""
        }

        val outcome = client.generateTags(requestFor())

        val success = assertIs<InferenceOutcome.Success>(outcome)
        assertTrue(success.tagging.concepts.isEmpty() && success.tagging.moods.isEmpty() && success.tagging.motifs.isEmpty())
    }

    @Test
    fun `a non-2xx status maps to MalformedResponse`() = runBlocking {
        val mockEngine = MockEngine { respond(content = "server error", status = HttpStatusCode.InternalServerError) }
        val client = LlamaCppTaggingClient(TEST_CONFIG, testHttpClient(mockEngine))

        val outcome = client.generateTags(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `an undecodable envelope maps to MalformedResponse`() = runBlocking {
        val client = clientRespondingWith { "not json at all" }

        val outcome = client.generateTags(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `content that fails to decode against the tagging schema maps to MalformedResponse`() = runBlocking {
        val client = clientRespondingWith {
            """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"not the expected json shape"}}]}"""
        }

        val outcome = client.generateTags(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `a finish_reason of length maps to MalformedResponse mentioning truncation, even with parseable content`() = runBlocking {
        val client = clientRespondingWith {
            """
            {
              "model": "test-model",
              "choices": [
                {
                  "message": { "role": "assistant", "content": "{\"concepts\":[{\"name\":\"death\",\"breadth\":\"broad\",\"relevance\":3,\"basis\":\"text\"}],\"moods\":[{\"name\":\"serene\",\"relevance\":2,\"basis\":\"text\"}],\"motifs\":[]}" },
                  "finish_reason": "length"
                }
              ]
            }
            """.trimIndent()
        }

        val outcome = client.generateTags(requestFor())

        val result = assertIs<InferenceOutcome.MalformedResponse>(outcome)
        assertTrue(result.message.contains("truncated"))
    }

    @Test
    fun `a connection exception maps to ConnectionFailure`() = runBlocking {
        val mockEngine = MockEngine { throw java.net.ConnectException("connection refused") }
        val client = LlamaCppTaggingClient(TEST_CONFIG, testHttpClient(mockEngine))

        val outcome = client.generateTags(requestFor())

        val result = assertIs<InferenceOutcome.ConnectionFailure>(outcome)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `the outgoing request includes the schema-constrained response_format and generation settings`() = runBlocking {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = ((request.body) as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"{\"concepts\":[],\"moods\":[],\"motifs\":[]}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LlamaCppTaggingClient(TEST_CONFIG, testHttpClient(mockEngine))

        client.generateTags(requestFor(GenerationSettings("test-model", 0.5, 500, reasoningEffort = "low")))

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

    private fun clientRespondingWith(body: () -> String): LlamaCppTaggingClient {
        val mockEngine = MockEngine {
            respond(
                content = body(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return LlamaCppTaggingClient(TEST_CONFIG, testHttpClient(mockEngine))
    }
}
