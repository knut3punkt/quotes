package no.esotericgames.quotes.server.imagegen.comfyui

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.imagegen.ComfyUiConfig
import kotlin.coroutines.cancellation.CancellationException

private const val ERROR_BODY_MAX_LENGTH = 300

/**
 * Talks to ComfyUI's HTTP API (`POST /prompt`, `GET /history/{id}`, `GET /view`, `POST /interrupt`) and
 * its WebSocket (`/ws?clientId=...`). All network and parse failures become [ComfyUiResult.Failure],
 * same spirit as the llama.cpp clients.
 *
 * The request timeout is set per HTTP call rather than as a client default, so it never applies to the
 * long-lived WebSocket session.
 */
class HttpComfyUiClient(
    private val config: ComfyUiConfig,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(WebSockets)
    },
) : ComfyUiClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun submitPrompt(workflow: JsonObject, clientId: String): ComfyUiResult<String> {
        val body = buildJsonObject {
            put("prompt", workflow)
            put("client_id", clientId)
        }
        return call("submit prompt", { post("${config.baseUrl}/prompt") { timed(); jsonBody(body.toString()) } }) { response ->
            val promptId = (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)
                ?.get("prompt_id")?.jsonPrimitive?.contentOrNull
            if (promptId == null) ComfyUiResult.Failure("ComfyUI accepted the prompt but returned no prompt_id")
            else ComfyUiResult.Success(promptId)
        }
    }

    override suspend fun fetchCompletion(promptId: String, saveNodeId: String): ComfyUiResult<PromptCompletion?> =
        call("fetch history", { get("${config.baseUrl}/history/$promptId") { timed() } }) { response ->
            val history = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@call ComfyUiResult.Failure("history response is not a JSON object")
            ComfyUiResult.Success(parseHistoryEntry(history, promptId, saveNodeId))
        }

    override suspend fun downloadImage(image: OutputImageRef): ComfyUiResult<ByteArray> =
        call("download image", {
            get("${config.baseUrl}/view") {
                timed()
                parameter("filename", image.filename)
                parameter("subfolder", image.subfolder)
                parameter("type", image.type)
            }
        }) { response -> ComfyUiResult.Success(response.readRawBytes()) }

    override suspend fun cancelPrompt(promptId: String) {
        try {
            // Drops the prompt if it is still pending; a no-op once it has started executing.
            httpClient.post("${config.baseUrl}/queue") {
                timed()
                jsonBody(buildJsonObject { put("delete", JsonArray(listOf(JsonPrimitive(promptId)))) }.toString())
            }
            httpClient.post("${config.baseUrl}/interrupt") {
                timed()
                // Newer ComfyUI versions only interrupt the named prompt; older ones ignore the body.
                jsonBody(buildJsonObject { put("prompt_id", promptId) }.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: if ComfyUI is unreachable there is nothing running to cancel anyway.
        }
    }

    override fun events(clientId: String): Flow<ComfyUiEvent> = channelFlow {
        val wsUrl = config.baseUrl.replaceFirst(Regex("^http"), "ws") + "/ws?clientId=$clientId"
        httpClient.webSocket(wsUrl) {
            for (frame in incoming) {
                if (frame is Frame.Text) ComfyUiEvent.parse(frame.readText())?.let { send(it) }
            }
        }
    }

    private fun HttpRequestBuilder.timed() {
        timeout { requestTimeoutMillis = config.requestTimeoutMillis }
    }

    private fun HttpRequestBuilder.jsonBody(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun <T> call(
        action: String,
        send: suspend HttpClient.() -> HttpResponse,
        handle: suspend (HttpResponse) -> ComfyUiResult<T>,
    ): ComfyUiResult<T> {
        val response = try {
            httpClient.send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Broad catch is intentional: CIO and HttpTimeout throw different types for refused
            // connections, timeouts and DNS failures, and none of them should reach the job loop.
            return ComfyUiResult.Failure("could not $action at ${config.baseUrl}: ${e.message ?: e::class.simpleName}")
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("").take(ERROR_BODY_MAX_LENGTH)
            return ComfyUiResult.Failure("could not $action: HTTP ${response.status}${if (body.isNotBlank()) " $body" else ""}")
        }
        return try {
            handle(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ComfyUiResult.Failure("could not $action: invalid response (${e.message ?: e::class.simpleName})")
        }
    }

    companion object {
        /**
         * Reads one prompt's entry from a `/history/{id}` response: `null` while the prompt has no entry
         * or isn't completed yet. Images from [saveNodeId] are preferred; any other node's saved outputs
         * are a fallback for an edited workflow that saves elsewhere.
         */
        fun parseHistoryEntry(history: JsonObject, promptId: String, saveNodeId: String): PromptCompletion? {
            val entry = history[promptId] as? JsonObject ?: return null
            val status = entry["status"] as? JsonObject
            val statusString = (status?.get("status_str") as? JsonPrimitive)?.contentOrNull
            val completed = (status?.get("completed") as? JsonPrimitive)?.contentOrNull == "true"
            if (statusString == "error") {
                return PromptCompletion.Failed(errorMessageFrom(status) ?: "ComfyUI reported an execution error")
            }
            val outputs = entry["outputs"] as? JsonObject ?: JsonObject(emptyMap())
            if (!completed && statusString != "success") return null

            fun imagesOf(nodeOutput: JsonObject?): List<OutputImageRef> =
                (nodeOutput?.get("images") as? JsonArray).orEmpty().mapNotNull { image ->
                    val fields = image as? JsonObject ?: return@mapNotNull null
                    val filename = (fields["filename"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    OutputImageRef(
                        filename = filename,
                        subfolder = (fields["subfolder"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        type = (fields["type"] as? JsonPrimitive)?.contentOrNull ?: "output",
                    )
                }

            val fromSaveNode = imagesOf(outputs[saveNodeId] as? JsonObject)
            val images = fromSaveNode.ifEmpty {
                outputs.values.flatMap { imagesOf(it as? JsonObject) }.filter { it.type == "output" }
            }
            return if (images.isEmpty()) {
                PromptCompletion.Failed("ComfyUI finished the prompt without saving an image")
            } else {
                PromptCompletion.Succeeded(images)
            }
        }

        /** Pulls `exception_message` out of the `execution_error` entry in a history status's messages. */
        private fun errorMessageFrom(status: JsonObject): String? {
            val messages = status["messages"] as? JsonArray ?: return null
            return messages.firstNotNullOfOrNull { message ->
                val pair = message as? JsonArray ?: return@firstNotNullOfOrNull null
                if ((pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull != "execution_error") return@firstNotNullOfOrNull null
                val details = pair.getOrNull(1) as? JsonObject
                listOfNotNull(
                    (details?.get("node_type") as? JsonPrimitive)?.contentOrNull,
                    (details?.get("exception_message") as? JsonPrimitive)?.contentOrNull?.trim(),
                ).joinToString(": ").ifEmpty { null }
            }
        }
    }
}
