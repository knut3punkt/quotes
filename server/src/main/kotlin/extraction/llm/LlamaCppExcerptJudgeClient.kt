package no.esotericgames.quotes.server.extraction.llm

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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig

private const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

/**
 * llama-server implementation of [ExcerptJudgeClient], following the same shape and failure
 * mapping as [LlamaCppExcerptSelectionClient]: every network/parse failure becomes a
 * [JudgeOutcome] value, never an exception. Configured separately from the selector so the judge can
 * run on a different (typically stronger, slower) model.
 */
class LlamaCppExcerptJudgeClient(
    private val llmConfig: ExtractionLlmConfig,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = llmConfig.requestTimeoutMillis
        }
    },
) : ExcerptJudgeClient {

    private val contentJson = Json { ignoreUnknownKeys = true }

    override suspend fun judgeStandalone(request: JudgeRequest): JudgeOutcome<StandaloneVerdict> =
        complete(request, STANDALONE_JUDGE_RESPONSE_FORMAT, StandaloneVerdictDto.serializer()) { it.toVerdict() }

    override suspend fun judgeFidelity(request: JudgeRequest): JudgeOutcome<FidelityVerdict> =
        complete(request, FIDELITY_JUDGE_RESPONSE_FORMAT, FidelityVerdictDto.serializer()) { it.toVerdict() }

    private suspend fun <Dto, Verdict> complete(
        request: JudgeRequest,
        responseFormat: JsonObject,
        deserializer: DeserializationStrategy<Dto>,
        toVerdict: (Dto) -> Verdict,
    ): JudgeOutcome<Verdict> {
        val body = ChatCompletionRequest(
            model = request.generation.model,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = request.userContent),
            ),
            temperature = request.generation.temperature,
            maxTokens = request.generation.maxOutputTokens,
            stream = false,
            responseFormat = responseFormat,
            reasoningEffort = request.generation.reasoningEffort,
        )

        val response = try {
            httpClient.post("${llmConfig.baseUrl}$CHAT_COMPLETIONS_PATH") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            // Broad catch is intentional, same rationale as LlamaCppExcerptSelectionClient.
            return JudgeOutcome.ConnectionFailure(e.message ?: e::class.simpleName ?: "connection failure")
        }

        if (!response.status.isSuccess()) {
            return JudgeOutcome.MalformedResponse("HTTP ${response.status}")
        }

        val envelope = try {
            response.body<ChatCompletionResponse>()
        } catch (e: Exception) {
            return JudgeOutcome.MalformedResponse("invalid chat-completion envelope: ${e.message}")
        }

        val content = envelope.choices.firstOrNull()?.message?.content
            ?: return JudgeOutcome.MalformedResponse("no choices in chat-completion response")

        val parsed = try {
            contentJson.decodeFromString(deserializer, content)
        } catch (e: Exception) {
            return JudgeOutcome.MalformedResponse("model content failed schema decode: ${e.message}")
        }

        return JudgeOutcome.Success(toVerdict(parsed), modelId = envelope.model)
    }
}
