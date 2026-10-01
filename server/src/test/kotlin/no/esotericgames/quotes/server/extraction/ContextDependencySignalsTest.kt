package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.UnitReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextDependencySignalsTest {

    @Test
    fun `flags a backward-pointing opener when the excerpt does not start the source`() {
        val signals = ContextDependencySignals.detect("This is why the method fails.", startUnit = 3, endUnit = 3, emptyList())

        assertEquals(listOf("This"), signals)
    }

    @Test
    fun `flags an opening connective`() {
        val signals = ContextDependencySignals.detect("But nothing could be done.", startUnit = 2, endUnit = 2, emptyList())

        assertEquals(listOf("But"), signals)
    }

    @Test
    fun `does not flag an opener when the excerpt starts the source`() {
        val signals = ContextDependencySignals.detect("This life is short.", startUnit = 1, endUnit = 1, emptyList())

        assertTrue(signals.isEmpty())
    }

    @Test
    fun `ignores leading quotation marks when reading the first word`() {
        val signals = ContextDependencySignals.detect("“Such men are rare.”", startUnit = 2, endUnit = 2, emptyList())

        assertEquals(listOf("Such"), signals)
    }

    @Test
    fun `does not flag an ordinary opening word`() {
        val signals = ContextDependencySignals.detect("Patience teaches more than waiting.", startUnit = 4, endUnit = 4, emptyList())

        assertTrue(signals.isEmpty())
    }

    @Test
    fun `flags backward phrases anywhere in the excerpt`() {
        val signals = ContextDependencySignals.detect(
            "Of the two, the latter is wiser.",
            startUnit = 1,
            endUnit = 1,
            emptyList(),
        )

        assertEquals(listOf("the latter"), signals)
    }

    @Test
    fun `flags selector references whose referent is outside the range, and deduplicates`() {
        val signals = ContextDependencySignals.detect(
            "This lesson stayed with her.",
            startUnit = 3,
            endUnit = 4,
            listOf(UnitReference("This", 2), UnitReference("her", 3), UnitReference("lesson", 1)),
        )

        assertEquals(listOf("This", "lesson"), signals)
    }
}
