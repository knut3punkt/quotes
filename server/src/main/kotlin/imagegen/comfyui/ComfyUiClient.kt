package no.esotericgames.quotes.server.imagegen.comfyui

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** Either a value or a failure message. ComfyUI calls never throw into the generation job. */
sealed interface ComfyUiResult<out T> {
    data class Success<T>(val value: T) : ComfyUiResult<T>
    data class Failure(val message: String) : ComfyUiResult<Nothing>
}

/** Where ComfyUI saved one output image, as listed in `/history`; the three fields are `/view`'s parameters. */
data class OutputImageRef(val filename: String, val subfolder: String, val type: String)

/** A finished prompt, as reported by `/history`. */
sealed interface PromptCompletion {
    data class Succeeded(val images: List<OutputImageRef>) : PromptCompletion
    data class Failed(val message: String) : PromptCompletion
}

/**
 * The server's view of a ComfyUI instance: submit an API-format workflow, poll its result, download the
 * output, interrupt it, and follow live progress over ComfyUI's WebSocket. Kept behind an interface so
 * the generation job is testable without a running ComfyUI.
 */
interface ComfyUiClient {
    /** Queues [workflow] and returns its `prompt_id`. */
    suspend fun submitPrompt(workflow: JsonObject, clientId: String): ComfyUiResult<String>

    /** The prompt's result, or `null` while it is still queued or running. */
    suspend fun fetchCompletion(promptId: String, saveNodeId: String): ComfyUiResult<PromptCompletion?>

    suspend fun downloadImage(image: OutputImageRef): ComfyUiResult<ByteArray>

    /** Removes [promptId] from the queue, or stops it if it is executing. Best effort; failures are ignored. */
    suspend fun cancelPrompt(promptId: String)

    /**
     * Live events for prompts submitted with [clientId]. The flow ends (normally or with an exception)
     * when the WebSocket closes; callers reconnect by collecting again.
     */
    fun events(clientId: String): Flow<ComfyUiEvent>
}
