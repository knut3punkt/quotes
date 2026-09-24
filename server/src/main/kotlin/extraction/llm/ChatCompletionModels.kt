package no.esotericgames.quotes.server.extraction.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false,
    @SerialName("response_format") val responseFormat: JsonElement,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val model: String? = null,
    val choices: List<ChatCompletionChoice> = emptyList(),
)

@Serializable
data class ChatCompletionChoice(val message: ChatMessageContent)

@Serializable
data class ChatMessageContent(val role: String? = null, val content: String? = null)
