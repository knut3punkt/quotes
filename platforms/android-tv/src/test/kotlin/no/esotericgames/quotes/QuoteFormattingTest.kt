package no.esotericgames.quotes

import kotlin.test.Test
import kotlin.test.assertEquals

class QuoteFormattingTest {

    @Test
    fun `formatAttribution prefixes the author with an em dash`() {
        assertEquals("— Alan Kay", formatAttribution("Alan Kay"))
    }
}
