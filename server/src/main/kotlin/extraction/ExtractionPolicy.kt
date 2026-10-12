package no.esotericgames.quotes.server.extraction

/**
 * Length buckets and score-acceptance thresholds from docs/features/quote-extraction.md's "Initial
 * length policy" and "LLM scoring" sections. There is no cap on the number of excerpts returned per
 * source — deterministic overlap/duplicate resolution already prevents redundant results.
 *
 * The score thresholds apply to the judge's verdict. The judge answers in anchored 1-5 levels mapped
 * to 0/25/50/75/100 (see [ExcerptJudge.levelToScore]), so the default of 75 means "level 4 or better".
 *
 * [acceptableMinWords] is low on purpose: a very short line ("One must imagine Sisyphus happy.") can be
 * the best excerpt in a passage, so the judge rather than the length gate decides.
 */
data class ExtractionPolicy(
    val minSourceWords: Int = 55,
    val preferredMinWords: Int = 12,
    val preferredMaxWords: Int = 45,
    val acceptableMinWords: Int = 3,
    val acceptableMaxWords: Int = 60,
    val minIndependence: Int = 75,
    val minCompleteness: Int = 75,
    val minContextualFidelity: Int = 75,
    val minQuotability: Int = 75,
)
