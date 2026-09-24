package no.esotericgames.quotes.server.extraction

/**
 * Length buckets and score-acceptance thresholds from docs/features/quote-extraction.md's "Initial
 * length policy" and "LLM scoring" sections. There is no cap on the number of excerpts returned per
 * source — deterministic overlap/duplicate resolution already prevents redundant results.
 */
data class ExtractionPolicy(
    val minSourceWords: Int = 55,
    val preferredMinWords: Int = 12,
    val preferredMaxWords: Int = 45,
    val acceptableMinWords: Int = 6,
    val acceptableMaxWords: Int = 60,
    val minIndependence: Int = 80,
    val minCompleteness: Int = 80,
    val minContextualFidelity: Int = 85,
    val minQuotability: Int = 65,
)
