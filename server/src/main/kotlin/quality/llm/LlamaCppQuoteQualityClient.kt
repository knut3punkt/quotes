package no.esotericgames.quotes.server.quality.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.llm.ChatCompletionRequest
import no.esotericgames.quotes.server.extraction.llm.ChatCompletionResponse
import no.esotericgames.quotes.server.extraction.llm.ChatMessage
import no.esotericgames.quotes.server.quality.QualityLlmConfig

private const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

/**
 * Talks to a local llama.cpp `llama-server` via its OpenAI-compatible `/v1/chat/completions`
 * endpoint, mirroring [no.esotericgames.quotes.server.interpretation.llm.LlamaCppInterpretationClient].
 * All network and parse failures map to [QualityOutcome]; nothing is thrown.
 */
class LlamaCppQuoteQualityClient(
    private val llmConfig: QualityLlmConfig,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = llmConfig.requestTimeoutMillis
        }
    },
) : QuoteQualityClient {

    private val contentJson = Json { ignoreUnknownKeys = true }

    override suspend fun assess(request: QualityRequest): QualityOutcome {
        val body = ChatCompletionRequest(
            model = request.generation.model,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = request.userContent),
            ),
            temperature = request.generation.temperature,
            maxTokens = request.generation.maxOutputTokens,
            stream = false,
            responseFormat = QUALITY_RESPONSE_FORMAT,
            reasoningEffort = request.generation.reasoningEffort,
        )

        val response = try {
            httpClient.post("${llmConfig.baseUrl}$CHAT_COMPLETIONS_PATH") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            // CIO/HttpTimeout throw different types for refused, timeout and DNS failures; none may escape.
            return QualityOutcome.ConnectionFailure(e.message ?: e::class.simpleName ?: "connection failure")
        }

        if (!response.status.isSuccess()) {
            return QualityOutcome.MalformedResponse("HTTP ${response.status}")
        }

        val envelope = try {
            response.body<ChatCompletionResponse>()
        } catch (e: Exception) {
            return QualityOutcome.MalformedResponse("invalid chat-completion envelope: ${e.message}")
        }

        val choice = envelope.choices.firstOrNull()
            ?: return QualityOutcome.MalformedResponse("no choices in chat-completion response")

        if (choice.finishReason == "length") {
            return QualityOutcome.MalformedResponse(
                "model response truncated: finish_reason=length (increase quality.llm.maxOutputTokens)",
            )
        }

        val content = choice.message.content
            ?: return QualityOutcome.MalformedResponse("no content in chat-completion response")

        val parsed = try {
            contentJson.decodeFromString(QualityResponseDto.serializer(), content)
        } catch (e: Exception) {
            return QualityOutcome.MalformedResponse("model content failed schema decode: ${e.message}")
        }

        return QualityOutcome.Success(parsed, modelId = envelope.model)
    }
}
