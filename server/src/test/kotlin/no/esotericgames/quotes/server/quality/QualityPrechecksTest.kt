package no.esotericgames.quotes.server.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun flags(text: String, language: String = "en", maxWords: Int = 55) = QualityPrechecks.detect(text, language, maxWords)

class QualityPrechecksTest {

    @Test
    fun `a clean short quote raises no flags`() {
        assertEquals(emptyList(), flags("Only dull people are brilliant at breakfast."))
        assertEquals(emptyList(), flags("It is easy, when everything is going well, to mistake good fortune for good judgment."))
    }

    @Test
    fun `length flags`() {
        assertTrue(PrecheckFlag.OVER_WORD_LIMIT in flags((1..60).joinToString(" ") { "the word$it" } + "."))
        assertTrue(PrecheckFlag.VERY_SHORT in flags("Ch. VIII"))
    }

    @Test
    fun `fragment flags`() {
        assertTrue(PrecheckFlag.LIST_FRAGMENT in flags("I forgive the stillborn hopes,"))
        assertTrue(PrecheckFlag.TRAILING_CONTINUATION in flags("I forgive the stillborn hopes,"))
        assertTrue(PrecheckFlag.LOWERCASE_START in flags("praying, working, teaching, reading, and not huddled together."))
        assertTrue(PrecheckFlag.TRAILING_CONTINUATION in flags("The person who exalts himself will be humbled; and"))
        assertTrue(PrecheckFlag.TRAILING_CONTINUATION in flags("I hear her hair has turned quite gold from grief"))
    }

    @Test
    fun `editorial residue flags`() {
        assertTrue(PrecheckFlag.BRACKETED_INSERTION in flags("I'm glad to know he's [Paneloux] better than his sermon."))
        assertTrue(PrecheckFlag.ELLIPSIS in flags("Work consists of whatever a body is OBLIGED to do, and...Play is the rest."))
        assertTrue(PrecheckFlag.EDITORIAL_PREFIX in flags("Variant translation: I know nothing except the fact of my ignorance."))
        assertTrue(PrecheckFlag.UNBALANCED_QUOTES in flags("\"Nothing is more despicable than respect based on fear."))
    }

    @Test
    fun `citation-only text is flagged, a quote that mentions a chapter is not`() {
        assertTrue(PrecheckFlag.CITATION_ONLY in flags("Bk. I, Ch. IV"))
        assertTrue(PrecheckFlag.CITATION_ONLY in flags("Maxims and Arrows, § 12"))
        assertTrue(PrecheckFlag.CITATION_ONLY !in flags("Every chapter of a life is written by the choices nobody else ever sees."))
    }

    @Test
    fun `non-English text is flagged by language or by a missing share of English function words`() {
        assertTrue(PrecheckFlag.LIKELY_NON_ENGLISH in flags("Was mich nicht umbringt, macht mich stärker, sagte er oft genug.", language = "en"))
        assertTrue(PrecheckFlag.LIKELY_NON_ENGLISH in flags("Anything at all.", language = "fr"))
        assertTrue(PrecheckFlag.LIKELY_NON_ENGLISH !in flags("The struggle itself toward the heights is enough to fill a man's heart."))
    }
}
