package no.esotericgames.quotes.server.dhammapada

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DhammapadaImportServiceTest {

    @Test
    fun `parseSegmentKey extracts verse and line number`() {
        assertEquals(1 to 1.0, parseSegmentKey("dhp1:1"))
        assertEquals(42 to 4.0, parseSegmentKey("dhp42:4"))
    }

    @Test
    fun `parseSegmentKey skips title and header segments`() {
        assertNull(parseSegmentKey("dhp1:0.1"))
        assertNull(parseSegmentKey("dhp1:0.4"))
    }

    @Test
    fun `parseSegmentKey rejects keys that do not match the expected shape`() {
        assertNull(parseSegmentKey("not-a-segment"))
        assertNull(parseSegmentKey("dhp:1"))
    }
}
