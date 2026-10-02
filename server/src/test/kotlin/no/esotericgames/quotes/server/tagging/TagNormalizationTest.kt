package no.esotericgames.quotes.server.tagging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagNormalizationTest {

    @Test
    fun `lowercases, collapses whitespace, and strips punctuation and leading articles`() {
        val normalized = TagNormalization.normalize("  The   Fear of Oblivion!  ", TagFacet.CONCEPT)

        assertEquals("fear of oblivion", normalized?.normalizedName)
        assertEquals("fear of oblivion", normalized?.displayName)
    }

    @Test
    fun `keeps hyphens and apostrophes inside a name`() {
        assertEquals("self-deception", TagNormalization.normalize("\"Self-Deception\"", TagFacet.CONCEPT)?.normalizedName)
        assertEquals("beginner's mind", TagNormalization.normalize("beginner's mind.", TagFacet.CONCEPT)?.normalizedName)
    }

    @Test
    fun `folds plurals for motifs only`() {
        assertEquals("star", TagNormalization.normalize("stars", TagFacet.MOTIF)?.normalizedName)
        assertEquals("night sky", TagNormalization.normalize("night skies", TagFacet.MOTIF)?.normalizedName)
        assertEquals("ash", TagNormalization.normalize("ashes", TagFacet.MOTIF)?.normalizedName)
        assertEquals("compass", TagNormalization.normalize("compass", TagFacet.MOTIF)?.normalizedName)
        assertEquals("sea", TagNormalization.normalize("sea", TagFacet.MOTIF)?.normalizedName)

        assertEquals("values", TagNormalization.normalize("values", TagFacet.CONCEPT)?.normalizedName)
    }

    @Test
    fun `a name with nothing left after cleaning is rejected`() {
        assertNull(TagNormalization.normalize(" ?! ", TagFacet.CONCEPT))
        assertNull(TagNormalization.normalize("the", TagFacet.MOOD))
    }

    @Test
    fun `normalizeKeepingDisplay keeps the admin's casing but normalizes the matching key`() {
        val normalized = TagNormalization.normalizeKeepingDisplay("  Fear of  God ", TagFacet.CONCEPT)

        assertEquals("Fear of God", normalized?.displayName)
        assertEquals("fear of god", normalized?.normalizedName)
    }
}
