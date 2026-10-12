package no.esotericgames.quotes.server.quality

import kotlinx.serialization.Serializable
import java.text.Normalizer

private const val MAX_FLAG_CODE_LENGTH = 40
private const val MAX_FLAG_LABEL_LENGTH = 60
private const val MAX_RATING_PROBLEMS = 20
private val COMBINING_MARKS_REGEX = Regex("""\p{M}+""")
private val NON_ALPHANUMERIC_REGEX = Regex("""[^A-Z0-9]+""")

/** The shape every problem code has, built-in or custom. */
val QUALITY_FLAG_CODE_REGEX = Regex("""[A-Z][A-Z0-9_]*""")

/** A problem flag a human can pick on the TV: a model problem, or one a human added. */
@Serializable
data class QualityFlag(val code: String, val label: String, val custom: Boolean)

/** A human's rating of one subject, checked and ready to store. */
data class ValidatedHumanRating(val verdict: QualityVerdict, val problems: List<String>)

/**
 * Human ratings from the TV remote (docs/features/quote-quality.md, "Human ratings on TV"). A rating has only a
 * verdict and problem flags, so it does not go through [QualityValidator], which checks a full model-shaped answer;
 * the rules here mirror it: unknown verdicts and flags are rejected, not dropped.
 */
object HumanRating {

    /**
     * The code a flag typed as [label] is stored under: accents dropped, upper case, runs of anything else as one
     * underscore. "Cliché" becomes `CLICHE`, so typing a model problem's name finds that problem.
     */
    fun flagCode(label: String): String {
        val folded = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS_REGEX, "")
        val code = folded.uppercase().replace(NON_ALPHANUMERIC_REGEX, "_").trim('_').take(MAX_FLAG_CODE_LENGTH).trimEnd('_')
        require(code.matches(QUALITY_FLAG_CODE_REGEX)) { "a flag needs a name that starts with a letter: \"$label\"" }
        require(PrecheckFlag.entries.none { it.name == code }) { "$code is a precheck flag, which the server computes itself" }
        return code
    }

    /** The label kept for a custom flag: trimmed, inner whitespace collapsed, and capped. */
    fun flagLabel(label: String): String = label.trim().replace(Regex("""\s+"""), " ").take(MAX_FLAG_LABEL_LENGTH)

    /** The model's problems, in their fixed order, then [custom] flags by label. */
    fun allFlags(custom: List<QualityFlag>): List<QualityFlag> =
        QualityProblem.entries.map { QualityFlag(it.name, it.label, custom = false) } + custom.sortedBy { it.label.lowercase() }

    /** Checks a rating against the verdict scale and the flags that exist ([customCodes] plus the model's problems). */
    fun validate(verdict: String, problems: List<String>, customCodes: Set<String>): ValidatedHumanRating {
        val parsedVerdict = QualityVerdict.fromDb(verdict.trim().lowercase())
            ?: throw IllegalArgumentException("unknown verdict \"$verdict\"")
        val codes = problems.map { it.trim().uppercase() }.distinct()
        require(codes.size <= MAX_RATING_PROBLEMS) { "at most $MAX_RATING_PROBLEMS problems per rating" }
        val known = QualityProblem.entries.map { it.name }.toSet() + customCodes
        val unknown = codes.filterNot { it in known }
        require(unknown.isEmpty()) { "unknown problems: ${unknown.joinToString()}" }
        return ValidatedHumanRating(parsedVerdict, codes)
    }
}
