package no.esotericgames.quotes.server.imagegen.comfyui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The subset of ComfyUI's WebSocket messages (`/ws?clientId=...`) the generation job uses. Every
 * message carries a `type` and a `data` object; binary frames (live previews) and unknown types are
 * ignored.
 */
sealed interface ComfyUiEvent {
    data class QueueStatus(val queueRemaining: Int) : ComfyUiEvent
    data class ExecutionStart(val promptId: String) : ComfyUiEvent
    data class ExecutionCached(val promptId: String) : ComfyUiEvent

    /** A node started executing; a `null` [nodeId] means the whole prompt has finished. */
    data class Executing(val promptId: String, val nodeId: String?) : ComfyUiEvent

    /** Sampler step progress of the node currently executing. */
    data class Progress(val promptId: String, val value: Int, val max: Int) : ComfyUiEvent

    data class ExecutionSuccess(val promptId: String) : ComfyUiEvent
    data class ExecutionError(val promptId: String, val message: String) : ComfyUiEvent
    data class ExecutionInterrupted(val promptId: String) : ComfyUiEvent

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses one text frame, or returns `null` for a message type or shape this server doesn't use. */
        fun parse(text: String): ComfyUiEvent? {
            val message = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            val type = message["type"].asStringOrNull() ?: return null
            val data = message["data"] as? JsonObject ?: return null
            if (type == "status") {
                val execInfo = (data["status"] as? JsonObject)?.get("exec_info") as? JsonObject
                return execInfo?.get("queue_remaining").asIntOrNull()?.let { QueueStatus(it) }
            }
            val promptId = data["prompt_id"].asStringOrNull() ?: return null
            return when (type) {
                "execution_start" -> ExecutionStart(promptId)
                "execution_cached" -> ExecutionCached(promptId)
                "executing" -> Executing(promptId, data["node"].asStringOrNull())
                "progress" -> {
                    val value = data["value"].asIntOrNull() ?: return null
                    val max = data["max"].asIntOrNull() ?: return null
                    Progress(promptId, value, max)
                }
                "execution_success" -> ExecutionSuccess(promptId)
                "execution_error" -> ExecutionError(
                    promptId,
                    listOfNotNull(data["node_type"].asStringOrNull(), data["exception_message"].asStringOrNull()?.trim())
                        .joinToString(": ")
                        .ifEmpty { "ComfyUI execution error" },
                )
                "execution_interrupted" -> ExecutionInterrupted(promptId)
                else -> null
            }
        }

        private fun JsonElement?.asStringOrNull(): String? =
            if (this is JsonPrimitive && this !is JsonNull) contentOrNull else null

        private fun JsonElement?.asIntOrNull(): Int? =
            if (this is JsonPrimitive && this !is JsonNull) intOrNull else null
    }
}
