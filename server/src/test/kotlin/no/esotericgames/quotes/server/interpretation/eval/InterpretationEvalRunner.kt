package no.esotericgames.quotes.server.interpretation.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.interpretation.InterpretationLlmConfig
import no.esotericgames.quotes.server.interpretation.InterpretationPromptBuilder
import no.esotericgames.quotes.server.interpretation.InterpretationPrompts
import no.esotericgames.quotes.server.interpretation.InterpretationValidator
import no.esotericgames.quotes.server.interpretation.QuoteInterpretationContext
import no.esotericgames.quotes.server.interpretation.llm.InferenceOutcome
import no.esotericgames.quotes.server.interpretation.llm.InterpretationRequest
import no.esotericgames.quotes.server.interpretation.llm.LlamaCppInterpretationClient
import java.io.File
import java.io.PrintStream

@Serializable
private data class EvalFixture(
    val id: String,
    val description: String,
    val quoteText: String,
    val authorName: String? = null,
    val sourceTitle: String? = null,
)

/**
 * Manually-invoked model-quality evaluation runner — deliberately NOT a `@Test`, so `./gradlew test`
 * never picks it up (per docs/features/quote-interpretations.md's "Evaluation corpus" section).
 * Mirrors [no.esotericgames.quotes.server.extraction.eval.ExtractionEvalRunner]'s shape: runs the
 * real pipeline (prompt building -> a real llama-server call -> deterministic validation) against the
 * curated corpus under src/test/resources/interpretation-eval/ and prints a human-readable report for
 * manual review against the interpretive-value/textual-support/distinctness/insight/overreach rubric.
 *
 * Run via "Run main()" in the IDE with a local llama-server already running.
 */
fun main() = runBlocking {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8)) // Windows consoles default to a non-UTF-8 codepage

    val fixtures = loadFixtures()
    if (fixtures.isEmpty()) {
        println("No fixtures found under src/test/resources/interpretation-eval/")
        return@runBlocking
    }

    val llmConfig = InterpretationLlmConfig(
        baseUrl = "http://localhost:8888",
        model = "local-model",
        temperature = 0.5,
        maxOutputTokens = 800,
        requestTimeoutMillis = 30_000,
    )
    val client = LlamaCppInterpretationClient(llmConfig)

    for (fixture in fixtures) {
        println("=".repeat(80))
        println("${fixture.id} — ${fixture.description}")
        println("-".repeat(80))
        println("\"${fixture.quoteText}\"")

        val context = QuoteInterpretationContext(
            text = fixture.quoteText,
            authorName = fixture.authorName,
            sourceTitle = fixture.sourceTitle,
        )
        val request = InterpretationRequest(
            systemPrompt = InterpretationPrompts.systemPrompt(),
            userContent = InterpretationPromptBuilder.buildUserContent(context),
            generation = GenerationSettings(llmConfig.model, llmConfig.temperature, llmConfig.maxOutputTokens),
        )

        when (val outcome = client.generateInterpretations(request)) {
            is InferenceOutcome.Success -> {
                val validated = InterpretationValidator.validate(outcome.candidates)
                if (validated.isEmpty()) {
                    println("No interpretations returned.")
                } else {
                    validated.forEach { interpretation ->
                        println()
                        println("[${interpretation.lens}] (textualSupport=${interpretation.textualSupport}, speculativeness=${interpretation.speculativeness}):")
                        println("  ${interpretation.interpretation}")
                    }
                }
            }
            is InferenceOutcome.ConnectionFailure ->
                println("Connection failure: ${outcome.message} (is llama-server running at ${llmConfig.baseUrl}?)")
            is InferenceOutcome.MalformedResponse -> println("Malformed response: ${outcome.message}")
        }
        println()
    }
}

private fun loadFixtures(): List<EvalFixture> {
    val resource = Thread.currentThread().contextClassLoader?.getResource("interpretation-eval") ?: return emptyList()
    val directory = File(resource.toURI())
    val json = Json { ignoreUnknownKeys = true }
    return directory.listFiles { file -> file.extension == "json" }
        ?.sortedBy { it.name }
        ?.map { json.decodeFromString(EvalFixture.serializer(), it.readText()) }
        ?: emptyList()
}
