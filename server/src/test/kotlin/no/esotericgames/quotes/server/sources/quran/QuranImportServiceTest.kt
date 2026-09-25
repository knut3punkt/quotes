package no.esotericgames.quotes.server.sources.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuranImportServiceTest {

    @Test
    fun `parseReference reads a single ayah`() {
        assertEquals(Triple(2, 255, 255), parseReference("2:255"))
    }

    @Test
    fun `parseReference reads an ayah range`() {
        assertEquals(Triple(94, 5, 6), parseReference("94:5-6"))
    }

    @Test
    fun `parseReference rejects malformed input`() {
        assertNull(parseReference("not-a-reference"))
        assertNull(parseReference("2"))
    }
}
