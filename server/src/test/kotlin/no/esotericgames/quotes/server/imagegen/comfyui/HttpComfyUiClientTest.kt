package no.esotericgames.quotes.server.imagegen.comfyui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.esotericgames.quotes.server.imagegen.ComfyUiConfig
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpComfyUiClientTest {

    private val config = ComfyUiConfig("http://comfy.local:8188", requestTimeoutMillis = 1000, imageTimeoutMillis = 10_000)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientWith(engine: MockEngine) = HttpComfyUiClient(config, HttpClient(engine) { install(HttpTimeout) })

    @Test
    fun `submit posts the workflow with the client id and returns the prompt id`() = runBlocking {
        var sentBody: JsonObject? = null
        val engine = MockEngine { request ->
            assertEquals("http://comfy.local:8188/prompt", request.url.toString())
            sentBody = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            respond("""{"prompt_id": "abc-123", "number": 4, "node_errors": {}}""", HttpStatusCode.OK, jsonHeaders)
        }
        val workflow = JsonObject(mapOf("1" to JsonObject(mapOf("class_type" to JsonPrimitive("KSampler")))))

        val result = clientWith(engine).submitPrompt(workflow, "client-1")

        assertEquals(ComfyUiResult.Success("abc-123"), result)
        val body = sentBody!!
        assertEquals("client-1", body.getValue("client_id").jsonPrimitive.content)
        assertEquals(workflow, body.getValue("prompt"))
    }

    @Test
    fun `a rejected workflow becomes a failure carrying ComfyUI's error body`() = runBlocking {
        val engine = MockEngine {
            respondError(HttpStatusCode.BadRequest, """{"error": {"message": "Prompt outputs failed validation"}}""")
        }

        val result = clientWith(engine).submitPrompt(JsonObject(emptyMap()), "client-1")

        assertIs<ComfyUiResult.Failure>(result)
        assertTrue("400" in result.message)
        assertTrue("failed validation" in result.message)
    }

    @Test
    fun `an unreachable server becomes a failure instead of an exception`() = runBlocking {
        val engine = MockEngine { throw ConnectException("Connection refused") }

        val result = clientWith(engine).fetchCompletion("abc", "461")

        assertIs<ComfyUiResult.Failure>(result)
        assertTrue("Connection refused" in result.message)
    }

    @Test
    fun `history is null while pending and lists the save node's images when done`() = runBlocking {
        var done = false
        val engine = MockEngine {
            val body = if (!done) {
                "{}"
            } else {
                """
                {"abc": {"outputs": {"461": {"images": [{"filename": "bg_00001_.png", "subfolder": "tvquotes/background", "type": "output"}]}},
                         "status": {"status_str": "success", "completed": true, "messages": []}}}
                """.trimIndent()
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val client = clientWith(engine)

        assertEquals(ComfyUiResult.Success(null), client.fetchCompletion("abc", "461"))
        done = true
        val completion = (client.fetchCompletion("abc", "461") as ComfyUiResult.Success).value

        assertEquals(
            PromptCompletion.Succeeded(listOf(OutputImageRef("bg_00001_.png", "tvquotes/background", "output"))),
            completion,
        )
    }

    @Test
    fun `history error status carries the exception message`() {
        val history = Json.parseToJsonElement(
            """
            {"abc": {"outputs": {}, "status": {"status_str": "error", "completed": false, "messages": [
                ["execution_start", {"prompt_id": "abc"}],
                ["execution_error", {"prompt_id": "abc", "node_type": "KSampler", "exception_message": "CUDA out of memory\n"}]
            ]}}}
            """.trimIndent(),
        ).jsonObject

        assertEquals(
            PromptCompletion.Failed("KSampler: CUDA out of memory"),
            HttpComfyUiClient.parseHistoryEntry(history, "abc", "461"),
        )
        assertNull(HttpComfyUiClient.parseHistoryEntry(history, "other", "461"))
    }

    @Test
    fun `a completed prompt without images is a failure`() {
        val history = Json.parseToJsonElement(
            """{"abc": {"outputs": {}, "status": {"status_str": "success", "completed": true, "messages": []}}}""",
        ).jsonObject

        assertIs<PromptCompletion.Failed>(HttpComfyUiClient.parseHistoryEntry(history, "abc", "461"))
    }

    @Test
    fun `download passes the image reference as view parameters`() = runBlocking {
        val png = byteArrayOf(1, 2, 3)
        val engine = MockEngine { request ->
            assertEquals("/view", request.url.encodedPath)
            assertEquals("bg.png", request.url.parameters["filename"])
            assertEquals("tvquotes/background", request.url.parameters["subfolder"])
            assertEquals("output", request.url.parameters["type"])
            respond(png, HttpStatusCode.OK)
        }

        val result = clientWith(engine).downloadImage(OutputImageRef("bg.png", "tvquotes/background", "output"))

        assertContentEquals(png, (result as ComfyUiResult.Success).value)
    }
}
