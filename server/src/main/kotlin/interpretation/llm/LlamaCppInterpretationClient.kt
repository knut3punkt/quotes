package no.esotericgames.quotes.server.interpretation.llm

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
import no.esotericgames.quotes.server.interpretation.InterpretationLlmConfig

private const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

/**
 * Talks to a local llama.cpp `llama-server` via its OpenAI-compatible `/v1/chat/completions`
 * endpoint, mirroring [no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptSelectionClient]
 * exactly. All network/parse failures are caught here and mapped to [InferenceOutcome] — never
 * thrown — since this is optional enrichment that must never propagate into the request cycle.
 * Reuses the generic [ChatCompletionRequest]/[ChatCompletionResponse]/[ChatMessage] wire DTOs from
 * the extraction feature's `llm` package, since those are already runtime-generic.
 */
class LlamaCppInterpretationClient(
    private val llmConfig: InterpretationLlmConfig,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = llmConfig.requestTimeoutMillis
        }
    },
) : InterpretationClient {

    private val contentJson = Json { ignoreUnknownKeys = true }

    override suspend fun generateInterpretations(request: InterpretationRequest): InferenceOutcome {
        val body = ChatCompletionRequest(
            model = request.generation.model,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = request.userContent),
            ),
            temperature = request.generation.temperature,
            maxTokens = request.generation.maxOutputTokens,
            stream = false,
            responseFormat = INTERPRETATION_RESPONSE_FORMAT,
            reasoningEffort = request.generation.reasoningEffort,
        )

        val response = try {
            httpClient.post("${llmConfig.baseUrl}$CHAT_COMPLETIONS_PATH") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            // Broad catch is intentional: CIO/HttpTimeout throw different exception types for
            // connection-refused, timeout, and DNS failure, and none of them should reach this
            // optional-enrichment call site.
            return InferenceOutcome.ConnectionFailure(e.message ?: e::class.simpleName ?: "connection failure")
        }

        if (!response.status.isSuccess()) {
            return InferenceOutcome.MalformedResponse("HTTP ${response.status}")
        }

        val envelope = try {
            response.body<ChatCompletionResponse>()
        } catch (e: Exception) {
            return InferenceOutcome.MalformedResponse("invalid chat-completion envelope: ${e.message}")
        }

        val choice = envelope.choices.firstOrNull()
            ?: return InferenceOutcome.MalformedResponse("no choices in chat-completion response")

        if (choice.finishReason == "length") {
            // The model ran out of output tokens before finishing. The content may still happen to be
            // valid, truncated-but-parseable JSON, so it must never be accepted as if it were complete.
            return InferenceOutcome.MalformedResponse(
                "model response truncated: finish_reason=length (increase interpretation.llm.maxOutputTokens)",
            )
        }

        val content = choice.message.content
            ?: return InferenceOutcome.MalformedResponse("no content in chat-completion response")

        val parsed = try {
            contentJson.decodeFromString(InterpretationResponseDto.serializer(), content)
        } catch (e: Exception) {
            return InferenceOutcome.MalformedResponse("model content failed schema decode: ${e.message}")
        }

        return InferenceOutcome.Success(parsed.interpretations.map { it.toRawCandidate() }, modelId = envelope.model)
    }
}
