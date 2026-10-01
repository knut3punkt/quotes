package no.esotericgames.quotes.server.extraction.llm

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
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TEST_CONFIG = ExtractionLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "judge-model",
    temperature = 0.1,
    maxOutputTokens = 500,
    requestTimeoutMillis = 5000,
)

private val REQUEST = JudgeRequest(systemPrompt = "system", userContent = "user", generation = GenerationSettings("judge-model", 0.1, 500))

class LlamaCppExcerptJudgeClientTest {

    @Test
    fun `a standalone verdict decodes to Success`() = runBlocking<Unit> {
        val client = clientRespondingWithContent(
            """{\"whatItIsAbout\":\"patience\",\"unresolvedReferences\":[\"this\"],\"insight\":\"waiting teaches\",\"standsAlone\":3,\"completeness\":5,\"quotability\":4}""",
        )

        val success = assertIs<JudgeOutcome.Success<StandaloneVerdict>>(client.judgeStandalone(REQUEST))

        assertEquals("judge-model", success.modelId)
        assertEquals(listOf("this"), success.verdict.unresolvedReferences)
        assertEquals(3, success.verdict.standsAlone)
        assertEquals(4, success.verdict.quotability)
    }

    @Test
    fun `a fidelity verdict decodes to Success`() = runBlocking<Unit> {
        val client = clientRespondingWithContent("""{\"meaningInSource\":\"same\",\"fidelity\":5,\"reason\":\"faithful\"}""")

        val success = assertIs<JudgeOutcome.Success<FidelityVerdict>>(client.judgeFidelity(REQUEST))

        assertEquals(5, success.verdict.fidelity)
    }

    @Test
    fun `content missing a required level maps to MalformedResponse`() = runBlocking<Unit> {
        val client = clientRespondingWithContent("""{\"whatItIsAbout\":\"patience\",\"insight\":\"x\"}""")

        assertIs<JudgeOutcome.MalformedResponse>(client.judgeStandalone(REQUEST))
    }

    @Test
    fun `a connection exception maps to ConnectionFailure`() = runBlocking<Unit> {
        val client = LlamaCppExcerptJudgeClient(TEST_CONFIG, testHttpClient(MockEngine { throw java.net.ConnectException("refused") }))

        assertIs<JudgeOutcome.ConnectionFailure>(client.judgeFidelity(REQUEST))
    }

    @Test
    fun `each call sends its own schema-constrained response_format`() = runBlocking<Unit> {
        val capturedBodies = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedBodies += ((request.body) as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"model":"judge-model","choices":[{"message":{"role":"assistant","content":"{}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LlamaCppExcerptJudgeClient(TEST_CONFIG, testHttpClient(mockEngine))

        client.judgeStandalone(REQUEST)
        client.judgeFidelity(REQUEST)

        assertTrue("excerpt_standalone_judgment" in capturedBodies[0])
        assertTrue("excerpt_fidelity_judgment" in capturedBodies[1])
    }

    private fun testHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
    }

    private fun clientRespondingWithContent(escapedContent: String): LlamaCppExcerptJudgeClient {
        val mockEngine = MockEngine {
            respond(
                content = """{"model":"judge-model","choices":[{"message":{"role":"assistant","content":"$escapedContent"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return LlamaCppExcerptJudgeClient(TEST_CONFIG, testHttpClient(mockEngine))
    }
}
