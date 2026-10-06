package no.esotericgames.quotes.server.admin

import io.ktor.http.parametersOf
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminFilterParametersTest {

    @Test
    fun `author keys round-trip, with blank and missing authors as none`() {
        assertEquals("name:Seneca", authorKeyOf("  Seneca "))
        assertEquals("none", authorKeyOf("   "))
        assertEquals("none", authorKeyOf(null))
        assertEquals(AuthorKey.Name("Seneca"), parseAuthorKey(authorKeyOf("Seneca")))
        assertEquals(AuthorKey.None, parseAuthorKey("none"))
    }

    @Test
    fun `an author literally named none is not confused with a missing author`() {
        assertEquals(AuthorKey.Name("none"), parseAuthorKey(authorKeyOf("none")))
    }

    @Test
    fun `source keys prefer the linked source over a suggested title`() {
        assertEquals("id:7", sourceKeyOf(7, "Letters"))
        assertEquals("raw:Letters", sourceKeyOf(null, " Letters "))
        assertEquals("none", sourceKeyOf(null, ""))
        assertEquals(SourceKey.Linked(7), parseSourceKey("id:7"))
        assertEquals(SourceKey.Suggested("Letters"), parseSourceKey("raw:Letters"))
        assertEquals(SourceKey.None, parseSourceKey("none"))
    }

    @Test
    fun `malformed keys are rejected`() {
        assertFailsWith<IllegalArgumentException> { parseAuthorKey("Seneca") }
        assertFailsWith<IllegalArgumentException> { parseAuthorKey("name:") }
        assertFailsWith<IllegalArgumentException> { parseSourceKey("id:abc") }
        assertFailsWith<IllegalArgumentException> { parseSourceKey("raw:") }
    }

    @Test
    fun `imported quote filter parses repeated and optional parameters`() {
        val filter = importedQuoteFilterFrom(
            parametersOf(
                "status" to listOf("pending", "rejected"),
                "author" to listOf("none", "name:Seneca"),
                "source" to listOf("id:3"),
                "possibleDuplicate" to listOf("flagged"),
                "importedFrom" to listOf("2026-10-01T00:00:00+02:00"),
                "minLength" to listOf("40"),
                "page" to listOf("3"),
            ),
            defaultPageSize = 100,
        )
        assertEquals(setOf("pending", "rejected"), filter.statuses)
        assertEquals(setOf(AuthorKey.None, AuthorKey.Name("Seneca")), filter.authors)
        assertEquals(setOf<SourceKey>(SourceKey.Linked(3)), filter.sources)
        assertEquals(true, filter.possibleDuplicate)
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), filter.importedFrom)
        assertNull(filter.importedBefore)
        assertEquals(40, filter.minLength)
        assertEquals(3, filter.page)
        assertEquals(100, filter.pageSize)
    }

    @Test
    fun `imported quote filter rejects bad timestamps and duplicate flags`() {
        assertFailsWith<IllegalArgumentException> {
            importedQuoteFilterFrom(parametersOf("importedFrom", "2026-10-01"), defaultPageSize = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            importedQuoteFilterFrom(parametersOf("possibleDuplicate", "maybe"), defaultPageSize = 100)
        }
    }

    @Test
    fun `id keys round-trip, with a missing reference as none`() {
        assertEquals("id:12", idKeyOf(12))
        assertEquals("none", idKeyOf(null))
        assertEquals(IdKey.Id(12), parseIdKey("id:12"))
        assertEquals(IdKey.None, parseIdKey("none"))
        assertFailsWith<IllegalArgumentException> { parseIdKey("12") }
    }

    @Test
    fun `quote filter parses repeated values for every multi-select filter`() {
        val filter = quoteFilterFrom(
            parametersOf(
                "author" to listOf("id:12", "none"),
                "source" to listOf("id:3"),
                "language" to listOf("en", "la"),
                "provider" to listOf("wikiquote"),
                "sourceConfidence" to listOf("sourced", "attributed"),
                "excerpts" to listOf("notRun", "none"),
                "tags" to listOf("has"),
            ),
            defaultPageSize = 50,
        )
        assertEquals(setOf(IdKey.Id(12), IdKey.None), filter.authors)
        assertEquals(setOf<IdKey>(IdKey.Id(3)), filter.sources)
        assertEquals(setOf("en", "la"), filter.languages)
        assertEquals(setOf("wikiquote"), filter.providers)
        assertEquals(setOf("sourced", "attributed"), filter.sourceConfidences)
        assertEquals(setOf(EnrichmentFilter.NOT_RUN, EnrichmentFilter.NONE_FOUND), filter.excerpts)
        assertEquals(emptySet(), filter.interpretations)
        assertEquals(setOf(EnrichmentFilter.HAS), filter.tags)
        assertEquals(50, filter.pageSize)
    }
}
