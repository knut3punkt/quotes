package no.esotericgames.quotes.server.admin

import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.quality.SubjectKind
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

const val QUALITY_NOT_ASSESSED = "notAssessed"
const val QUALITY_FAILED = "failed"

/** `value = ANY(column)` for a text-array column. */
private class ArrayContains(private val column: Column<List<String>>, private val value: String) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(stringParam(value))
        queryBuilder.append(" = ANY(")
        queryBuilder.append(column)
        queryBuilder.append(")")
    }
}

internal fun anyOf(conditions: List<Op<Boolean>>): Op<Boolean> = conditions.reduce { acc, op -> acc or op }

/** The outer query's whole-quote assessment in [runId], for use in a correlated `EXISTS`. */
internal fun wholeQuoteAssessment(runId: Int): Query =
    QuoteQualityAssessments.select(QuoteQualityAssessments.id).where {
        (QuoteQualityAssessments.runId eq runId) and
            (QuoteQualityAssessments.quoteId eq Quotes.id) and
            (QuoteQualityAssessments.subjectKind eq SubjectKind.QUOTE.dbValue)
    }

/** The outer query's excerpt's assessment in [runId], for use in a correlated `EXISTS`. */
internal fun excerptAssessment(runId: Int): Query =
    QuoteQualityAssessments.select(QuoteQualityAssessments.id).where {
        (QuoteQualityAssessments.runId eq runId) and
            (QuoteQualityAssessments.excerptId eq QuoteExcerpts.id) and
            (QuoteQualityAssessments.subjectKind eq SubjectKind.EXCERPT.dbValue)
    }

/**
 * Whether the subject behind [assessment] (a correlated query from [wholeQuoteAssessment] or [excerptAssessment],
 * built fresh for each use) matches the quality filter: any of [verdicts] (plus `notAssessed` and `failed`), and
 * any of [flags] (model problems or precheck flags). Null when neither is set.
 */
internal fun qualityMatches(verdicts: Set<String>, flags: Set<String>, assessment: () -> Query): Op<Boolean>? {
    val conditions = buildList {
        if (verdicts.isNotEmpty()) add(anyOf(verdicts.map { verdictCondition(it, assessment) }))
        if (flags.isNotEmpty()) {
            add(
                exists(
                    assessment().andWhere {
                        anyOf(
                            flags.flatMap { flag ->
                                listOf(
                                    ArrayContains(QuoteQualityAssessments.problems, flag),
                                    ArrayContains(QuoteQualityAssessments.precheckFlags, flag),
                                )
                            },
                        )
                    },
                ),
            )
        }
    }
    return conditions.takeIf { it.isNotEmpty() }?.reduce { acc, op -> acc and op }
}

private fun verdictCondition(verdict: String, assessment: () -> Query): Op<Boolean> = when (verdict) {
    QUALITY_NOT_ASSESSED -> notExists(assessment())
    QUALITY_FAILED -> exists(assessment().andWhere { QuoteQualityAssessments.status eq "failed" })
    else -> exists(assessment().andWhere { QuoteQualityAssessments.verdict eq verdict })
}
