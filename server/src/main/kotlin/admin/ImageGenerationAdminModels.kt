package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable

/** One tag in an image generation run, in queue order. Times are epoch milliseconds. */
@Serializable
data class ImageGenerationItem(
    val tagId: Int,
    val tagName: String,
    val facet: String, // "mood" | "motif"
    val kind: String, // "background" | "element"
    // "queued" | "submitting" | "waiting" (in ComfyUI's queue) | "running" | "saving" | "done" | "failed" | "cancelled"
    val status: String,
    val prompt: String? = null,
    val seed: Long? = null,
    val step: Int? = null,
    val maxSteps: Int? = null,
    val error: String? = null,
    val filePath: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
)

/** The whole image generation job, as pushed over `/admin/image-generation/ws`. */
@Serializable
data class ImageGenerationJobState(
    val status: String, // "idle" | "running" | "cancelling" | "finished" | "cancelled"
    val comfyUiBaseUrl: String,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    // ComfyUI's own queue length, including prompts from other clients; null until the WebSocket reports it.
    val comfyQueueRemaining: Int? = null,
    // Set when ComfyUI's WebSocket is unreachable; progress then falls back to polling, without step counts.
    val connectionWarning: String? = null,
    val items: List<ImageGenerationItem> = emptyList(),
)
