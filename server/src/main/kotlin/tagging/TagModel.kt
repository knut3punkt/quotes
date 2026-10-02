package no.esotericgames.quotes.server.tagging

/**
 * The three tag facets (docs/features/quote-tagging.md). Each has a different future consumer:
 * concepts feed search, moods feed background atmosphere, motifs feed layered symbol/image elements.
 */
enum class TagFacet(val dbValue: String) {
    CONCEPT("concept"),
    MOOD("mood"),
    MOTIF("motif"),
    ;

    companion object {
        fun fromDbValue(value: String): TagFacet? = entries.firstOrNull { it.dbValue == value }
    }
}

/** Only meaningful for [TagFacet.CONCEPT]: a broad theme versus a granular idea. */
enum class TagBreadth(val dbValue: String) {
    BROAD("broad"),
    SPECIFIC("specific"),
    ;

    companion object {
        fun fromDbValue(value: String): TagBreadth? = entries.firstOrNull { it.dbValue == value }
    }
}

/** Whether a tag comes from the quotation's surface text or from one of its interpretive readings. */
enum class TagBasis(val dbValue: String) {
    TEXT("text"),
    INTERPRETATION("interpretation"),
    ;

    companion object {
        fun fromDbValue(value: String): TagBasis? = entries.firstOrNull { it.dbValue == value }
    }
}

const val MIN_RELEVANCE = 1
const val MAX_RELEVANCE = 3

const val ORIGIN_LLM = "llm"
const val ORIGIN_ADMIN = "admin"
