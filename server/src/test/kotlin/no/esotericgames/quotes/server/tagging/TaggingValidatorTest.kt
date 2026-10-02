package no.esotericgames.quotes.server.tagging

import no.esotericgames.quotes.server.tagging.llm.RawTagCandidate
import no.esotericgames.quotes.server.tagging.llm.RawTagging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun concept(name: String, breadth: String? = "specific", relevance: Int = 2, basis: String = "text") =
    RawTagCandidate(name, breadth, relevance, basis)

private fun tag(name: String, relevance: Int = 2, basis: String = "text") = RawTagCandidate(name, null, relevance, basis)

private fun tagging(
    concepts: List<RawTagCandidate> = emptyList(),
    moods: List<RawTagCandidate> = emptyList(),
    motifs: List<RawTagCandidate> = emptyList(),
) = RawTagging(concepts, moods, motifs)

class TaggingValidatorTest {

    @Test
    fun `valid tags of every facet pass with their facet, breadth and basis`() {
        val result = TaggingValidator.validate(
            tagging(
                concepts = listOf(concept("Death", breadth = "broad", relevance = 3)),
                moods = listOf(tag("serene")),
                motifs = listOf(tag("rivers", basis = "interpretation")),
            ),
            DEFAULT_TAGGING_POLICY,
        )

        assertEquals(3, result.size)
        val death = result.single { it.facet == TagFacet.CONCEPT }
        assertEquals("death", death.name.normalizedName)
        assertEquals(TagBreadth.BROAD, death.breadth)
        assertEquals(3, death.relevance)
        val river = result.single { it.facet == TagFacet.MOTIF }
        assertEquals("river", river.name.normalizedName)
        assertEquals(TagBasis.INTERPRETATION, river.basis)
        assertEquals(null, river.breadth)
    }

    @Test
    fun `malformed candidates are dropped individually`() {
        val result = TaggingValidator.validate(
            tagging(
                concepts = listOf(
                    concept("impermanence"),
                    concept("no breadth", breadth = null),
                    concept("bad breadth", breadth = "medium"),
                    concept("bad basis", basis = "vibes"),
                    concept("   "),
                    concept("a concept with far too many words in it"),
                ),
                moods = listOf(tag("quietly hopeful and calm")),
            ),
            DEFAULT_TAGGING_POLICY,
        )

        assertEquals(listOf("impermanence"), result.map { it.name.normalizedName })
    }

    @Test
    fun `relevance is clamped to 1-3`() {
        val result = TaggingValidator.validate(
            tagging(concepts = listOf(concept("low", relevance = 0), concept("high", relevance = 9))),
            DEFAULT_TAGGING_POLICY,
        )

        assertEquals(mapOf("high" to 3, "low" to 1), result.associate { it.name.normalizedName to it.relevance })
    }

    @Test
    fun `duplicates within a facet merge, keeping the strongest relevance and text basis`() {
        val result = TaggingValidator.validate(
            tagging(
                concepts = listOf(
                    concept("Self-deception", relevance = 1, basis = "interpretation"),
                    concept("self-deception", relevance = 3, basis = "text"),
                ),
                motifs = listOf(tag("star"), tag("stars")),
            ),
            DEFAULT_TAGGING_POLICY,
        )

        val selfDeception = result.single { it.facet == TagFacet.CONCEPT }
        assertEquals(3, selfDeception.relevance)
        assertEquals(TagBasis.TEXT, selfDeception.basis)
        assertEquals(1, result.count { it.facet == TagFacet.MOTIF })
    }

    @Test
    fun `the same name may appear in different facets`() {
        val result = TaggingValidator.validate(
            tagging(concepts = listOf(concept("light")), motifs = listOf(tag("light"))),
            DEFAULT_TAGGING_POLICY,
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `each facet is capped at the policy limit, keeping the most relevant`() {
        val policy = DEFAULT_TAGGING_POLICY.copy(maxMoods = 1, maxMotifs = 2)
        val result = TaggingValidator.validate(
            tagging(
                moods = listOf(tag("wry", relevance = 1), tag("solemn", relevance = 3)),
                motifs = listOf(tag("sea", relevance = 1), tag("lamp", relevance = 2), tag("seed", relevance = 3)),
            ),
            policy,
        )

        assertEquals(listOf("solemn"), result.filter { it.facet == TagFacet.MOOD }.map { it.name.normalizedName })
        assertEquals(listOf("seed", "lamp"), result.filter { it.facet == TagFacet.MOTIF }.map { it.name.normalizedName })
    }

    @Test
    fun `an empty response validates to no tags`() {
        assertTrue(TaggingValidator.validate(tagging(), DEFAULT_TAGGING_POLICY).isEmpty())
    }
}
