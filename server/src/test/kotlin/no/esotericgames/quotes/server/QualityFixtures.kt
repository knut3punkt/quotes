package no.esotericgames.quotes.server

import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.Quotes
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import java.util.UUID

/**
 * Builds quotes, excerpts and quality assessments in the dev database for DB-backed tests, under a fresh author
 * and run so other data never interferes. Every call must run inside a transaction; [delete] removes it all.
 */
class QualityFixtures {
    private val marker = UUID.randomUUID().toString()
    private val quoteIds = mutableListOf<Int>()
    private val runIds = mutableListOf<Int>()

    val runLabel = "quality fixture $marker"

    val authorId: Int = Authors.insert {
        it[name] = "Quality fixture $marker"
        it[normalizedName] = "quality fixture $marker"
    }[Authors.id]

    val runId: Int = newRun(runLabel)

    /** Another run, for comparing runs; [delete] removes it too. */
    fun newRun(label: String): Int = QuoteQualityRuns.insert {
        it[QuoteQualityRuns.label] = label
        it[runner] = "external"
        it[methodVersion] = "test"
        it[promptVersion] = "test"
    }[QuoteQualityRuns.id].also(runIds::add)

    fun quote(text: String): Int = Quotes.insert {
        it[Quotes.text] = text
        it[normalizedText] = "$text $marker"
        it[Quotes.authorId] = this@QualityFixtures.authorId
    }[Quotes.id].also(quoteIds::add)

    /** An accepted excerpt of [quoteId], with no assessment. */
    fun excerpt(quoteId: Int, text: String): Int {
        val attemptId = QuoteExtractionAttempts.insert {
            it[QuoteExtractionAttempts.quoteId] = quoteId
            it[extractionMethodVersion] = "test"
            it[promptVersion] = "test"
            it[status] = "succeeded"
        }[QuoteExtractionAttempts.id]
        return QuoteExcerpts.insert {
            it[QuoteExcerpts.attemptId] = attemptId
            it[QuoteExcerpts.quoteId] = quoteId
            it[QuoteExcerpts.text] = text
            it[startOffset] = 0
            it[endOffset] = text.length
            it[startUnit] = 0
            it[endUnit] = 0
            it[wordCount] = text.split(" ").size
            it[independenceScore] = 90
            it[completenessScore] = 90
            it[quotabilityScore] = 90
            it[contextFidelityScore] = 90
            it[meetsThresholds] = true
        }[QuoteExcerpts.id]
    }

    /** A succeeded assessment in [inRun]: of the excerpt when [excerptId] is given, else of the whole quote. */
    fun assess(
        quoteId: Int,
        excerptId: Int?,
        text: String,
        verdict: String,
        problems: List<String> = emptyList(),
        precheckFlags: List<String> = emptyList(),
        inRun: Int = runId,
    ) {
        QuoteQualityAssessments.insert {
            it[QuoteQualityAssessments.runId] = inRun
            it[QuoteQualityAssessments.quoteId] = quoteId
            it[QuoteQualityAssessments.excerptId] = excerptId
            it[subjectKind] = if (excerptId == null) "quote" else "excerpt"
            it[subjectText] = text
            it[subjectHash] = "$marker:$text"
            it[wordCount] = text.split(" ").size
            it[status] = "succeeded"
            it[QuoteQualityAssessments.verdict] = verdict
            it[unresolvedReferences] = emptyList()
            it[QuoteQualityAssessments.problems] = problems
            it[QuoteQualityAssessments.precheckFlags] = precheckFlags
        }
    }

    /** An accepted excerpt of [quoteId], assessed with [verdict]. */
    fun assessedExcerpt(quoteId: Int, text: String, verdict: String): Int =
        excerpt(quoteId, text).also { assess(quoteId, it, text, verdict) }

    fun delete() {
        QuoteQualityRuns.deleteWhere { QuoteQualityRuns.id inList runIds }
        Quotes.deleteWhere { Quotes.id inList quoteIds }
        Authors.deleteWhere { Authors.id eq authorId }
    }
}
