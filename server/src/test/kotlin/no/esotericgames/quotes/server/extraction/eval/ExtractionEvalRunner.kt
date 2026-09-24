package no.esotericgames.quotes.server.extraction.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig
import no.esotericgames.quotes.server.extraction.ExtractionPolicy
import no.esotericgames.quotes.server.extraction.ExtractionPromptBuilder
import no.esotericgames.quotes.server.extraction.ExtractionPrompts
import no.esotericgames.quotes.server.extraction.ExtractionValidator
import no.esotericgames.quotes.server.extraction.segmentSourceIntoUnits
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionRequest
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.InferenceOutcome
import no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptSelectionClient
import java.io.File
import java.io.PrintStream

@Serializable
private data class EvalFixture(val id: String, val description: String, val sourceText: String)

/**
 * Manually-invoked model-quality evaluation runner — deliberately NOT a `@Test`, so `./gradlew test`
 * never picks it up (per docs/features/quote-extraction.md's "Evaluation" section). Runs the real
 * pipeline (segmentation -> a real llama-server call -> deterministic validation) against the
 * curated corpus under src/test/resources/extraction-eval/ and prints a human-readable report for
 * manual review against the independence/completeness/quotability/contextualFidelity rubric.
 *
 * Run via "Run main()" in the IDE with a local llama-server already running, or via the optional
 * `runExtractionEval` Gradle task if one has been added to server/build.gradle.kts.
 */
fun main() = runBlocking {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8)) // Windows consoles default to a non-UTF-8 codepage

    val fixtures = loadFixtures()
    if (fixtures.isEmpty()) {
        println("No fixtures found under src/test/resources/extraction-eval/")
        return@runBlocking
    }

    val llmConfig = ExtractionLlmConfig(
        baseUrl = "http://localhost:8888",
        model = "local-model",
        temperature = 0.1,
        maxOutputTokens = 800,
        requestTimeoutMillis = 30_000,
    )
    val policy = ExtractionPolicy()
    val client = LlamaCppExcerptSelectionClient(llmConfig)

    for (fixture in fixtures) {
        println("=".repeat(80))
        println("${fixture.id} — ${fixture.description}")
        println("-".repeat(80))

        val units = segmentSourceIntoUnits(fixture.sourceText)
        println("Units (${units.size}):")
        units.forEach { println("  [${it.id}] ${it.text}") }

        val request = ExcerptSelectionRequest(
            systemPrompt = ExtractionPrompts.systemPrompt(),
            userContent = ExtractionPromptBuilder.buildUserContent(units),
            generation = GenerationSettings(llmConfig.model, llmConfig.temperature, llmConfig.maxOutputTokens),
        )

        when (val outcome = client.selectExcerpts(request)) {
            is InferenceOutcome.Success -> {
                val validated = ExtractionValidator.validate(fixture.sourceText, units, outcome.candidates, policy)
                if (validated.isEmpty()) {
                    println("No excerpts returned.")
                } else {
                    validated.forEach { excerpt ->
                        println()
                        println("Excerpt [${excerpt.startUnit}-${excerpt.endUnit}] (meetsThresholds=${excerpt.meetsThresholds}):")
                        println("  \"${excerpt.text}\"")
                        println(
                            "  independence=${excerpt.independence} completeness=${excerpt.completeness} " +
                                "quotability=${excerpt.quotability} contextualFidelity=${excerpt.contextualFidelity}",
                        )
                        println("  reason: ${excerpt.reason}")
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
    val resource = Thread.currentThread().contextClassLoader?.getResource("extraction-eval") ?: return emptyList()
    val directory = File(resource.toURI())
    val json = Json { ignoreUnknownKeys = true }
    return directory.listFiles { file -> file.extension == "json" }
        ?.sortedBy { it.name }
        ?.map { json.decodeFromString(EvalFixture.serializer(), it.readText()) }
        ?: emptyList()
}
