package no.esotericgames.quotes.server.sources.bhagavadgita

import kotlin.test.Test
import kotlin.test.assertEquals

class BhagavadGitaImportServiceTest {

    @Test
    fun `stripLeadingCitation removes the chapter-dot-verse prefix`() {
        assertEquals(
            "Wherever is the Lord Shri Krishna, the Prince of Wisdom.",
            stripLeadingCitation("18.78 Wherever is the Lord Shri Krishna, the Prince of Wisdom."),
        )
    }

    @Test
    fun `stripLeadingCitation leaves text without a citation prefix untouched`() {
        assertEquals(
            "Dhritarashtra said - what happened, O Sanjaya?",
            stripLeadingCitation("Dhritarashtra said - what happened, O Sanjaya?"),
        )
    }

    @Test
    fun `stripLeadingCitation trims surrounding whitespace`() {
        assertEquals("Hello.", stripLeadingCitation("  2.47 Hello.  "))
    }
}
