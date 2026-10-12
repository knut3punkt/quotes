package no.esotericgames.quotes.server.quality

/** The overall judgment, best first. [rank] orders verdicts for comparisons (4 = excellent ... 0 = reject). */
enum class QualityVerdict(val dbValue: String, val rank: Int) {
    EXCELLENT("excellent", 4),
    GOOD("good", 3),
    ACCEPTABLE("acceptable", 2),
    WEAK("weak", 1),
    REJECT("reject", 0),
    ;

    /** Worth keeping on screen; weak and reject are the ones to review for unapproval. */
    val isKeep: Boolean get() = rank >= ACCEPTABLE.rank

    companion object {
        fun fromDb(value: String): QualityVerdict? = entries.firstOrNull { it.dbValue == value }
    }
}

/** Problems the model may report, as defined in prompts/quote-quality-v1.md. */
enum class QualityProblem {
    FRAGMENT,
    CONTEXT_DEPENDENT,
    NARRATION_OR_DIALOGUE,
    EDITORIAL_OR_META,
    POSSIBLY_MISATTRIBUTED,
    NON_ENGLISH,
    FLAT,
    CLICHE,
    TOO_LONG,
    SENSITIVE_OR_DATED,
}

enum class SubjectKind(val dbValue: String) {
    QUOTE("quote"),
    EXCERPT("excerpt"),
    ;

    companion object {
        fun fromDb(value: String): SubjectKind =
            entries.firstOrNull { it.dbValue == value } ?: throw IllegalArgumentException("unknown subject kind: $value")
    }
}

/** An `llm` run is assessed by the server's configured model; an `external` run is uploaded by another runner. */
enum class QualityRunner(val dbValue: String) {
    LLM("llm"),
    EXTERNAL("external"),
    ;

    companion object {
        fun fromDb(value: String): QualityRunner =
            entries.firstOrNull { it.dbValue == value } ?: throw IllegalArgumentException("unknown runner: $value")
    }
}
