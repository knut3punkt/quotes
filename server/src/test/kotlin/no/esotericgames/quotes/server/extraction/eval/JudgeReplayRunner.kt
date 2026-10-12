package no.esotericgames.quotes.server.extraction.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.extraction.ExcerptJudge
import no.esotericgames.quotes.server.extraction.ExcerptJudgePrompts
import no.esotericgames.quotes.server.extraction.ExtractionLlmConfig
import no.esotericgames.quotes.server.extraction.ExtractionPolicy
import no.esotericgames.quotes.server.extraction.llm.FidelityVerdict
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.JudgeOutcome
import no.esotericgames.quotes.server.extraction.llm.JudgeRequest
import no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.StandaloneVerdict
import java.io.File
import java.io.PrintStream

/** One stored, already-judged candidate, exported from `quote_excerpts` with its stored verdict. */
@Serializable
private data class ReplaySample(
    val id: Int,
    val quoteId: Int,
    val stratum: String,
    val sourceText: String,
    val text: String,
    val contextSignals: List<String> = emptyList(),
    val mistral: StoredVerdict,
)

@Serializable
private data class StoredVerdict(
    val independence: Int,
    val completeness: Int,
    val quotability: Int,
    val fidelity: Int? = null,
    val meetsThresholds: Boolean,
    val judgeNotes: String? = null,
)

@Serializable
private data class ReplayStandalone(
    val whatItIsAbout: String = "",
    val unresolvedReferences: List<String> = emptyList(),
    val insight: String = "",
    val standsAlone: Int,
    val completeness: Int,
    val quotability: Int,
) {
    fun toVerdict() = StandaloneVerdict(whatItIsAbout, unresolvedReferences, insight, standsAlone, completeness, quotability)
}

@Serializable
private data class ReplayFidelity(val meaningInSource: String = "", val fidelity: Int, val reason: String = "")

/** One replayed judgment. The fidelity call is made for every candidate, so it can be compared per call. */
@Serializable
private data class ReplayVerdict(val id: Int, val standalone: ReplayStandalone, val fidelity: ReplayFidelity? = null)

/** The judge prompt pair a replay uses, and the acceptance rule that went with it. */
private enum class JudgeVersion(val standalonePath: String, val fidelityPath: String) {
    V3("/prompts/excerpt-judge-standalone-v3.md", "/prompts/excerpt-judge-fidelity-v2.md"),
    V4("/prompts/excerpt-judge-standalone-v4.md", "/prompts/excerpt-judge-fidelity-v3.md"),
    ;

    fun fidelityUserContent(sample: ReplaySample, reading: String): String = when (this) {
        // v3 compared the source with the blind reader's paraphrase; kept here so v3 stays replayable.
        V3 -> "SOURCE PASSAGE\n\n${sample.sourceText}\n\nEXCERPT\n\n${sample.text}\n\nSTANDALONE READER'S UNDERSTANDING\n\n$reading"
        V4 -> ExcerptJudgePrompts.buildFidelityUserContent(sample.sourceText, sample.text)
    }

    fun passes(verdict: ReplayVerdict, sample: ReplaySample, policy: ExtractionPolicy): Boolean {
        val standalone = verdict.standalone.toVerdict()
        val passesStandalone = when (this) {
            V3 -> standalone.unresolvedReferences.none { it.isNotBlank() } &&
                standalone.insight.isNotBlank() &&
                listOf(standalone.standsAlone, standalone.completeness, standalone.quotability).all { it >= 4 }
            V4 -> ExcerptJudge.passesStandalone(standalone, sample.contextSignals, policy)
        }
        return passesStandalone && (verdict.fidelity?.fidelity ?: 0) >= 4
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Manually-invoked replay of the extraction judge on stored candidates (not a `@Test`), so a model and
 * a prompt version can be compared on exactly the same inputs (docs/features/quote-extraction.md,
 * "Judge replay"). Two modes:
 *
 * - `llm <sample.jsonl> <out.jsonl> <v3|v4>`: replays every sample through the local judge model
 *   (EXTRACTION_JUDGE_LLM_BASE_URL / EXTRACTION_JUDGE_LLM_MODEL, as in [main] of ExtractionEvalRunner).
 * - `report <sample.jsonl> <out.md> <label>=<verdicts.jsonl>:<v3|v4> ...`: compares each replay with
 *   the stored verdicts, per stratum, and lists the disagreements.
 *
 * The sample is a JSONL export of `quote_excerpts` rows with their quote text and stored scores.
 * Verdict files written by other runners (for example a frontier model following the same prompts)
 * use the same [ReplayVerdict] shape.
 */
fun main(args: Array<String>) = runBlocking {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
    when (args.firstOrNull()) {
        "llm" -> replayWithLocalModel(File(args[1]), File(args[2]), JudgeVersion.valueOf(args[3].uppercase()))
        "report" -> writeReport(File(args[1]), File(args[2]), args.drop(3))
        else -> println("usage: llm <sample> <out> <v3|v4> | report <sample> <out.md> <label>=<verdicts>:<v3|v4>...")
    }
}

private fun readSamples(file: File): List<ReplaySample> =
    file.readLines().filter { it.isNotBlank() }.map { json.decodeFromString(ReplaySample.serializer(), it) }

private fun readVerdicts(file: File): Map<Int, ReplayVerdict> =
    file.readLines().filter { it.isNotBlank() }
        .map { json.decodeFromString(ReplayVerdict.serializer(), it) }
        .associateBy { it.id }

private suspend fun replayWithLocalModel(sampleFile: File, outFile: File, version: JudgeVersion) {
    val config = ExtractionLlmConfig(
        baseUrl = System.getenv("EXTRACTION_JUDGE_LLM_BASE_URL") ?: System.getenv("EXTRACTION_LLM_BASE_URL") ?: "http://localhost:8888",
        model = System.getenv("EXTRACTION_JUDGE_LLM_MODEL") ?: "local-model",
        temperature = 0.1,
        maxOutputTokens = 700,
        requestTimeoutMillis = 120_000,
    )
    val client = LlamaCppExcerptJudgeClient(config)
    val generation = GenerationSettings(config.model, config.temperature, config.maxOutputTokens)
    val standalonePrompt = loadResource(version.standalonePath)
    val fidelityPrompt = loadResource(version.fidelityPath)
    val samples = readSamples(sampleFile)
    println("Replaying ${samples.size} candidates with judge $version on ${config.model} @ ${config.baseUrl}")

    outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
        for (sample in samples) {
            val standaloneContent = ExcerptJudgePrompts.buildStandaloneUserContent(sample.text, sample.contextSignals)
            val standalone = when (val outcome = client.judgeStandalone(JudgeRequest(standalonePrompt, standaloneContent, generation))) {
                is JudgeOutcome.Success -> outcome.verdict
                else -> {
                    println("  ${sample.id}: standalone call failed: $outcome")
                    continue
                }
            }
            val fidelityContent = version.fidelityUserContent(sample, standalone.whatItIsAbout)
            val fidelity: FidelityVerdict? =
                when (val outcome = client.judgeFidelity(JudgeRequest(fidelityPrompt, fidelityContent, generation))) {
                    is JudgeOutcome.Success -> outcome.verdict
                    else -> {
                        println("  ${sample.id}: fidelity call failed: $outcome")
                        null
                    }
                }
            val verdict = ReplayVerdict(
                id = sample.id,
                standalone = ReplayStandalone(
                    standalone.whatItIsAbout, standalone.unresolvedReferences, standalone.insight,
                    standalone.standsAlone, standalone.completeness, standalone.quotability,
                ),
                fidelity = fidelity?.let { ReplayFidelity(it.meaningInSource, it.fidelity, it.reason) },
            )
            writer.write(json.encodeToString(ReplayVerdict.serializer(), verdict))
            writer.newLine()
            println("  ${sample.id}: ${standalone.standsAlone}/${standalone.completeness}/${standalone.quotability}/${fidelity?.fidelity}")
        }
    }
}

private data class ReplayRun(val label: String, val verdicts: Map<Int, ReplayVerdict>, val version: JudgeVersion)

private fun writeReport(sampleFile: File, outFile: File, runSpecs: List<String>) {
    val policy = ExtractionPolicy()
    val samples = readSamples(sampleFile)
    val runs = runSpecs.map { spec ->
        val (label, rest) = spec.split("=", limit = 2)
        val path = rest.substringBeforeLast(":")
        ReplayRun(label, readVerdicts(File(path)), JudgeVersion.valueOf(rest.substringAfterLast(":").uppercase()))
    }
    fun accepted(run: ReplayRun, sample: ReplaySample): Boolean? =
        run.verdicts[sample.id]?.let { run.version.passes(it, sample, policy) }

    val report = buildString {
        appendLine("# Judge replay")
        appendLine()
        appendLine("${samples.size} stored candidates. \"stored\" is the verdict Ministral 3 8B gave in production (judge v3).")
        appendLine()
        appendLine("## Accepted per stratum")
        appendLine()
        appendLine("| Stratum | n | stored | " + runs.joinToString(" | ") { it.label } + " |")
        appendLine("|---|---|---|" + runs.joinToString("") { "---|" })
        samples.groupBy { it.stratum }.toSortedMap().forEach { (stratum, group) ->
            val cells = runs.map { run -> group.count { accepted(run, it) == true }.toString() + "/" + group.count { run.verdicts.containsKey(it.id) } }
            appendLine("| $stratum | ${group.size} | ${group.count { it.mistral.meetsThresholds }} | ${cells.joinToString(" | ")} |")
        }
        appendLine()
        appendLine("## Agreement with the stored verdict")
        appendLine()
        appendLine("| Run | both accept | stored only | run only | both reject | agreement |")
        appendLine("|---|---|---|---|---|---|")
        for (run in runs) {
            val pairs = samples.mapNotNull { sample -> accepted(run, sample)?.let { sample.mistral.meetsThresholds to it } }
            val agreement = pairs.count { it.first == it.second } * 100 / pairs.size.coerceAtLeast(1)
            appendLine(
                "| ${run.label} | ${pairs.count { it.first && it.second }} | ${pairs.count { it.first && !it.second }} | " +
                    "${pairs.count { !it.first && it.second }} | ${pairs.count { !it.first && !it.second }} | $agreement% |",
            )
        }
        appendLine()
        appendLine("## Level distributions")
        appendLine()
        appendLine("| Run | quotability 1-5 | fidelity 1-5 | references listed |")
        appendLine("|---|---|---|---|")
        for (run in runs) {
            val verdicts = run.verdicts.values
            val quotability = (1..5).joinToString("/") { level -> verdicts.count { it.standalone.quotability == level }.toString() }
            val fidelity = (1..5).joinToString("/") { level -> verdicts.count { it.fidelity?.fidelity == level }.toString() }
            val listed = verdicts.count { verdict -> verdict.standalone.unresolvedReferences.any { it.isNotBlank() } }
            appendLine("| ${run.label} | $quotability | $fidelity | $listed/${verdicts.size} |")
        }
        for (run in runs) {
            appendLine()
            appendLine("## Disagreements: ${run.label} vs stored")
            appendLine()
            samples.filter { sample -> accepted(run, sample).let { it != null && it != sample.mistral.meetsThresholds } }.forEach { sample ->
                val verdict = run.verdicts.getValue(sample.id)
                val standalone = verdict.standalone
                val decision = if (sample.mistral.meetsThresholds) "stored accepts, ${run.label} rejects" else "stored rejects, ${run.label} accepts"
                appendLine("- **${sample.id}** ($decision): \"${sample.text.replace("\n", " ")}\"")
                appendLine(
                    "  - ${run.label}: ${standalone.standsAlone}/${standalone.completeness}/${standalone.quotability}/" +
                        "${verdict.fidelity?.fidelity}, unresolved ${standalone.unresolvedReferences}, insight \"${standalone.insight}\"",
                )
                appendLine("  - stored: ${sample.mistral.judgeNotes?.replace("\n", " | ")}")
            }
        }
    }
    outFile.writeText(report, Charsets.UTF_8)
    println("Wrote ${outFile.path}")
}

private fun loadResource(path: String): String =
    (object {}).javaClass.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: error("bundled resource not found: $path")
