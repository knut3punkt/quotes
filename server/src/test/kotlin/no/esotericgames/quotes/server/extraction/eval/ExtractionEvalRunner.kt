package no.esotericgames.quotes.server.extraction.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.ExcerptJudge
import no.esotericgames.quotes.server.extraction.ExcerptJudgePrompts
import no.esotericgames.quotes.server.extraction.ExcerptJudgment
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig
import no.esotericgames.quotes.server.extraction.ExtractionPolicy
import no.esotericgames.quotes.server.extraction.ExtractionPromptBuilder
import no.esotericgames.quotes.server.extraction.ExtractionPrompts
import no.esotericgames.quotes.server.extraction.ExtractionValidator
import no.esotericgames.quotes.server.extraction.ValidatedExcerpt
import no.esotericgames.quotes.server.extraction.segmentSourceIntoUnits
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionRequest
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.InferenceOutcome
import no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptSelectionClient
import java.io.File
import java.io.PrintStream

/** A unit range, using the unit ids the runner prints for the fixture's source text. */
@Serializable
private data class UnitRange(val start: Int, val end: Int, val why: String = "")

/**
 * Gold labels are optional, so unlabeled fixtures still run. [goodRanges] should be accepted,
 * [badRanges] are tempting ranges that must be rejected (dangling references, flat prose), and
 * [expectEmpty] marks a source for which accepting anything at all is wrong.
 */
@Serializable
private data class EvalFixture(
    val id: String,
    val description: String,
    val sourceText: String,
    val goodRanges: List<UnitRange> = emptyList(),
    val badRanges: List<UnitRange> = emptyList(),
    val expectEmpty: Boolean = false,
)

private class Tally {
    var acceptedGood = 0
    var acceptedBad = 0
    var acceptedUnlabeled = 0
    var missedGood = 0
    var falseNonEmpty = 0
    var failures = 0
}

/**
 * Manually-invoked model-quality evaluation runner — deliberately NOT a `@Test`, so `./gradlew test`
 * never picks it up (per docs/features/quote-extraction.md's "Evaluation" section). Runs the real
 * pipeline (segmentation -> selection call -> deterministic validation -> judge calls -> overlap
 * resolution) against the curated corpus under src/test/resources/extraction-eval/, prints every
 * candidate with the judge's reading, and ends with a summary against the fixtures' gold labels, so
 * prompt or model changes can be compared run to run.
 *
 * Endpoints and models come from the same environment variables as application.conf
 * (EXTRACTION_LLM_BASE_URL, EXTRACTION_LLM_MODEL, EXTRACTION_JUDGE_LLM_BASE_URL,
 * EXTRACTION_JUDGE_LLM_MODEL), defaulting to a local llama-server on port 8888.
 *
 * Run via "Run main()" in the IDE with a local llama-server already running.
 */
fun main() = runBlocking {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8)) // Windows consoles default to a non-UTF-8 codepage

    val fixtures = loadFixtures()
    if (fixtures.isEmpty()) {
        println("No fixtures found under src/test/resources/extraction-eval/")
        return@runBlocking
    }

    val selectorConfig = ExtractionLlmConfig(
        baseUrl = System.getenv("EXTRACTION_LLM_BASE_URL") ?: "http://localhost:8888",
        model = System.getenv("EXTRACTION_LLM_MODEL") ?: "local-model",
        temperature = 0.1,
        maxOutputTokens = 800,
        requestTimeoutMillis = 60_000,
    )
    val judgeConfig = selectorConfig.copy(
        baseUrl = System.getenv("EXTRACTION_JUDGE_LLM_BASE_URL") ?: selectorConfig.baseUrl,
        model = System.getenv("EXTRACTION_JUDGE_LLM_MODEL") ?: selectorConfig.model,
        maxOutputTokens = 500,
    )
    val policy = ExtractionPolicy()
    val selector = LlamaCppExcerptSelectionClient(selectorConfig)
    val judge = ExcerptJudge(
        LlamaCppExcerptJudgeClient(judgeConfig),
        GenerationSettings(judgeConfig.model, judgeConfig.temperature, judgeConfig.maxOutputTokens),
        policy,
    )
    val tally = Tally()

    println("Selector: ${ExtractionPrompts.PROMPT_VERSION} on ${selectorConfig.model} @ ${selectorConfig.baseUrl}")
    println("Judge:    ${ExcerptJudgePrompts.PROMPT_VERSION} on ${judgeConfig.model} @ ${judgeConfig.baseUrl}")

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
            generation = GenerationSettings(selectorConfig.model, selectorConfig.temperature, selectorConfig.maxOutputTokens),
        )

        val candidates = when (val outcome = selector.selectExcerpts(request)) {
            is InferenceOutcome.Success -> ExtractionValidator.validate(fixture.sourceText, units, outcome.candidates, policy)
            is InferenceOutcome.ConnectionFailure -> {
                println("Selector connection failure: ${outcome.message} (is llama-server running at ${selectorConfig.baseUrl}?)")
                tally.failures++
                continue
            }
            is InferenceOutcome.MalformedResponse -> {
                println("Selector malformed response: ${outcome.message}")
                tally.failures++
                continue
            }
        }
        println("Selector proposed ${candidates.size} structurally valid candidate(s).")

        val judged = mutableListOf<ValidatedExcerpt>()
        for (candidate in candidates) {
            when (val judgment = judge.judge(fixture.sourceText, candidate)) {
                is ExcerptJudgment.Judged -> judged += judgment.excerpt
                is ExcerptJudgment.Unreachable -> println("  judge unreachable for [${candidate.startUnit}-${candidate.endUnit}]: ${judgment.message}")
                is ExcerptJudgment.Malformed -> println("  judge malformed for [${candidate.startUnit}-${candidate.endUnit}]: ${judgment.message}")
            }
        }
        judged.sortedBy { it.startUnit }.forEach { printExcerpt(it, fixture) }

        val accepted = ExtractionValidator.resolveOverlaps(judged).filter { it.meetsThresholds }
        println()
        println("Accepted: ${accepted.joinToString { "[${it.startUnit}-${it.endUnit}]" }.ifEmpty { "(none)" }}")
        tallyFixture(fixture, accepted, tally)
        println()
    }

    println("=".repeat(80))
    println("SUMMARY (${fixtures.size} fixtures)")
    println("  accepted good ranges:        ${tally.acceptedGood}")
    println("  accepted BAD ranges:         ${tally.acceptedBad}")
    println("  accepted unlabeled ranges:   ${tally.acceptedUnlabeled}")
    println("  missed good ranges:          ${tally.missedGood}")
    println("  expected-empty but accepted: ${tally.falseNonEmpty}")
    println("  selector failures:           ${tally.failures}")
}

private fun label(excerpt: ValidatedExcerpt, fixture: EvalFixture): String {
    fun UnitRange.matches() = start == excerpt.startUnit && end == excerpt.endUnit
    return when {
        fixture.goodRanges.any { it.matches() } -> "GOOD"
        fixture.badRanges.any { it.matches() } -> "BAD (${fixture.badRanges.first { it.matches() }.why})"
        else -> "unlabeled"
    }
}

private fun printExcerpt(excerpt: ValidatedExcerpt, fixture: EvalFixture) {
    println()
    val verdict = if (excerpt.meetsThresholds) "PASS" else "reject"
    println("[${excerpt.startUnit}-${excerpt.endUnit}] $verdict — gold: ${label(excerpt, fixture)}")
    println("  \"${excerpt.text}\"")
    println(
        "  independence=${excerpt.independence} completeness=${excerpt.completeness} " +
            "quotability=${excerpt.quotability} contextualFidelity=${excerpt.contextualFidelity ?: "-"}",
    )
    if (excerpt.contextSignals.isNotEmpty()) println("  signals: ${excerpt.contextSignals.joinToString(", ")}")
    println("  selector: ${excerpt.reason}")
    excerpt.judgeNotes.lines().forEach { println("  judge: $it") }
}

private fun tallyFixture(fixture: EvalFixture, accepted: List<ValidatedExcerpt>, tally: Tally) {
    fun ValidatedExcerpt.isIn(ranges: List<UnitRange>) = ranges.any { it.start == startUnit && it.end == endUnit }

    accepted.forEach { excerpt ->
        when {
            excerpt.isIn(fixture.goodRanges) -> tally.acceptedGood++
            excerpt.isIn(fixture.badRanges) -> tally.acceptedBad++
            else -> tally.acceptedUnlabeled++
        }
    }
    tally.missedGood += fixture.goodRanges.count { range -> accepted.none { it.startUnit == range.start && it.endUnit == range.end } }
    if (fixture.expectEmpty && accepted.isNotEmpty()) tally.falseNonEmpty++
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
