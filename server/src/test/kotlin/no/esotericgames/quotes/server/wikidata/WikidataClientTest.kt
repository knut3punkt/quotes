package no.esotericgames.quotes.server.wikidata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikidataClientTest {

    @Test
    fun `parseYear reads a common-era date`() {
        assertEquals(1875, parseYear("+1875-07-26T00:00:00Z"))
    }

    @Test
    fun `parseYear reads a before-common-era date as negative`() {
        assertEquals(-384, parseYear("-0384-01-01T00:00:00Z"))
    }

    @Test
    fun `parseYear rejects a malformed value`() {
        assertNull(parseYear("not-a-date"))
    }
}
