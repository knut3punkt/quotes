package no.esotericgames.quotes.server.tagging.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.tagging.DEFAULT_TAGGING_LLM_CONFIG
import no.esotericgames.quotes.server.tagging.DEFAULT_TAGGING_POLICY
import no.esotericgames.quotes.server.tagging.InterpretationContext
import no.esotericgames.quotes.server.tagging.QuoteTaggingContext
import no.esotericgames.quotes.server.tagging.TagFacet
import no.esotericgames.quotes.server.tagging.TaggingPromptBuilder
import no.esotericgames.quotes.server.tagging.TaggingPrompts
import no.esotericgames.quotes.server.tagging.TaggingValidator
import no.esotericgames.quotes.server.tagging.ValidatedTag
import no.esotericgames.quotes.server.tagging.VocabularyHint
import no.esotericgames.quotes.server.tagging.llm.InferenceOutcome
import no.esotericgames.quotes.server.tagging.llm.LlamaCppTaggingClient
import no.esotericgames.quotes.server.tagging.llm.TaggingRequest
import java.io.File
import java.io.PrintStream

@Serializable
private data class EvalInterpretation(
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
)

@Serializable
private data class EvalFixture(
    val id: String,
    val description: String,
    val quoteText: String,
    val authorName: String? = null,
    val sourceTitle: String? = null,
    val interpretations: List<EvalInterpretation> = emptyList(),
)

/**
 * Manually invoked model-quality evaluation for tagging, not a `@Test`, so `./gradlew test` never runs
 * it. Runs prompt building, a real llama-server call and validation against the fixtures under
 * src/test/resources/tagging-eval/, and prints the tags for manual review (see
 * docs/features/quote-tagging.md's "Evaluation" section).
 *
 * The fixtures run in order against a vocabulary that grows from the earlier fixtures' tags, the way
 * the real vocabulary hint grows, so later fixtures show whether the model reuses existing tags.
 *
 * The endpoint and model come from TAGGING_LLM_BASE_URL and TAGGING_LLM_MODEL, defaulting to a local
 * llama-server on port 8888. Run with `.\gradlew.bat :server:runTaggingEval` or "Run main()" in the IDE.
 */
fun main() = runBlocking {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8)) // Windows consoles default to a non-UTF-8 codepage

    val fixtures = loadFixtures()
    if (fixtures.isEmpty()) {
        println("No fixtures found under src/test/resources/tagging-eval/")
        return@runBlocking
    }

    val llmConfig = DEFAULT_TAGGING_LLM_CONFIG.copy(
        baseUrl = System.getenv("TAGGING_LLM_BASE_URL") ?: DEFAULT_TAGGING_LLM_CONFIG.baseUrl,
        model = System.getenv("TAGGING_LLM_MODEL") ?: DEFAULT_TAGGING_LLM_CONFIG.model,
        requestTimeoutMillis = 60_000,
    )
    val client = LlamaCppTaggingClient(llmConfig)
    val usage = mutableMapOf<TagFacet, MutableMap<String, Int>>()

    println("Tagging: ${TaggingPrompts.PROMPT_VERSION} on ${llmConfig.model} @ ${llmConfig.baseUrl}")

    for (fixture in fixtures) {
        println("=".repeat(80))
        println("${fixture.id} — ${fixture.description}")
        println("-".repeat(80))
        println("\"${fixture.quoteText}\"")

        val context = QuoteTaggingContext(
            text = fixture.quoteText,
            authorName = fixture.authorName,
            sourceTitle = fixture.sourceTitle,
            interpretations = fixture.interpretations.map {
                InterpretationContext(it.lens, it.interpretation, it.textualSupport, it.speculativeness)
            },
        )
        val request = TaggingRequest(
            systemPrompt = TaggingPrompts.systemPrompt(),
            userContent = TaggingPromptBuilder.buildUserContent(context, hintFrom(usage)),
            generation = GenerationSettings(llmConfig.model, llmConfig.temperature, llmConfig.maxOutputTokens),
        )

        when (val outcome = client.generateTags(request)) {
            is InferenceOutcome.Success -> {
                val validated = TaggingValidator.validate(outcome.tagging, DEFAULT_TAGGING_POLICY)
                printTags(validated, usage)
                validated.forEach { tag ->
                    val facetUsage = usage.getOrPut(tag.facet) { mutableMapOf() }
                    facetUsage[tag.name.displayName] = (facetUsage[tag.name.displayName] ?: 0) + 1
                }
            }
            is InferenceOutcome.ConnectionFailure ->
                println("Connection failure: ${outcome.message} (is llama-server running at ${llmConfig.baseUrl}?)")
            is InferenceOutcome.MalformedResponse -> println("Malformed response: ${outcome.message}")
        }
        println()
    }
}

/** Prints each facet's tags; a `*` marks a tag the vocabulary already had from an earlier fixture. */
private fun printTags(tags: List<ValidatedTag>, usageBefore: Map<TagFacet, Map<String, Int>>) {
    if (tags.isEmpty()) {
        println("No tags returned.")
        return
    }
    TagFacet.entries.forEach { facet ->
        val facetTags = tags.filter { it.facet == facet }
        val rendered = facetTags.joinToString(", ") { tag ->
            val reused = if (usageBefore[facet]?.containsKey(tag.name.displayName) == true) "*" else ""
            val breadth = tag.breadth?.let { " ${it.dbValue}" } ?: ""
            val basis = if (tag.basis.dbValue == "interpretation") " interp" else ""
            "$reused${tag.name.displayName} (r${tag.relevance}$breadth$basis)"
        }
        println("${facet.dbValue.padEnd(8)} ${rendered.ifEmpty { "—" }}")
    }
}

private fun hintFrom(usage: Map<TagFacet, Map<String, Int>>): VocabularyHint {
    fun names(facet: TagFacet) = usage[facet].orEmpty().entries.sortedByDescending { it.value }.map { it.key }
    return VocabularyHint(concepts = names(TagFacet.CONCEPT), moods = names(TagFacet.MOOD), motifs = names(TagFacet.MOTIF))
}

private fun loadFixtures(): List<EvalFixture> {
    val resource = Thread.currentThread().contextClassLoader?.getResource("tagging-eval") ?: return emptyList()
    val directory = File(resource.toURI())
    val json = Json { ignoreUnknownKeys = true }
    return directory.listFiles { file -> file.extension == "json" }
        ?.sortedBy { it.name }
        ?.map { json.decodeFromString(EvalFixture.serializer(), it.readText()) }
        ?: emptyList()
}
