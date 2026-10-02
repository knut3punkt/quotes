package no.esotericgames.quotes.server.tagging

import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.Tags
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

data class TagUsage(
    val id: Int,
    val facet: TagFacet,
    val name: String,
    val breadth: TagBreadth?,
    val usageCount: Long,
)

// A merge always re-points existing aliases at the new target, so chains stay one hop long. The limit
// only guards against a cycle introduced by hand in the database.
private const val MAX_ALIAS_HOPS = 10

/**
 * The open vocabulary's database side. Every function runs inside the caller's transaction.
 *
 * A name resolves through aliases: a tag merged into another keeps its row with `merged_into_id` set,
 * so when the model (or an admin) uses the old name again it lands on the canonical tag instead of
 * re-creating the duplicate.
 */
object TagVocabulary {

    /** Returns the canonical tag id for [name], creating the tag if the facet has no tag by that name yet. */
    fun resolveOrCreate(facet: TagFacet, name: NormalizedTagName, breadth: TagBreadth?, createdBy: String): Int {
        findTagId(facet, name.normalizedName)?.let { return canonicalId(it) }

        Tags.insertIgnore {
            it[Tags.facet] = facet.dbValue
            it[Tags.name] = name.displayName
            it[normalizedName] = name.normalizedName
            it[Tags.breadth] = breadth?.dbValue
            it[Tags.createdBy] = createdBy
        }
        val id = findTagId(facet, name.normalizedName) ?: error("tag insert for '${name.normalizedName}' did not persist")
        return canonicalId(id)
    }

    fun canonicalId(tagId: Int): Int {
        var currentId = tagId
        repeat(MAX_ALIAS_HOPS) {
            val mergedInto = Tags.select(Tags.mergedIntoId).where { Tags.id eq currentId }.firstOrNull()
                ?.get(Tags.mergedIntoId)
                ?: return currentId
            currentId = mergedInto
        }
        error("tag $tagId has an alias chain longer than $MAX_ALIAS_HOPS hops")
    }

    /** Canonical (unmerged) tags of a facet, most used first. Rejected assignments don't count as usage. */
    fun listCanonical(facet: TagFacet?, search: String?, limit: Int): List<TagUsage> {
        val usageCount = QuoteTagAssignments.id.count()
        val query = Tags
            .join(
                QuoteTagAssignments,
                JoinType.LEFT,
                onColumn = Tags.id,
                otherColumn = QuoteTagAssignments.tagId,
                additionalConstraint = { QuoteTagAssignments.rejected eq false },
            )
            .select(Tags.id, Tags.facet, Tags.name, Tags.breadth, usageCount)
            .where { Tags.mergedIntoId.isNull() }
        facet?.let { query.andWhere { Tags.facet eq it.dbValue } }
        search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { term ->
            query.andWhere { Tags.normalizedName like "%$term%" }
        }

        return query
            .groupBy(Tags.id, Tags.facet, Tags.name, Tags.breadth)
            .orderBy(usageCount to SortOrder.DESC, Tags.name to SortOrder.ASC)
            .limit(limit)
            .map {
                TagUsage(
                    id = it[Tags.id],
                    facet = TagFacet.fromDbValue(it[Tags.facet]) ?: error("unknown facet ${it[Tags.facet]}"),
                    name = it[Tags.name],
                    breadth = it[Tags.breadth]?.let(TagBreadth::fromDbValue),
                    usageCount = it[usageCount],
                )
            }
    }

    fun loadHint(policy: TaggingPolicy): VocabularyHint = VocabularyHint(
        concepts = listCanonical(TagFacet.CONCEPT, search = null, limit = policy.conceptHintSize).map { it.name },
        moods = listCanonical(TagFacet.MOOD, search = null, limit = policy.moodHintSize).map { it.name },
        motifs = listCanonical(TagFacet.MOTIF, search = null, limit = policy.motifHintSize).map { it.name },
    )

    private fun findTagId(facet: TagFacet, normalizedName: String): Int? =
        Tags.selectAll().where { (Tags.facet eq facet.dbValue) and (Tags.normalizedName eq normalizedName) }
            .firstOrNull()
            ?.get(Tags.id)
}

/** Matches the assignments of one subject: the whole quote when [excerptId] is null, otherwise that excerpt. */
fun quoteTagSubjectMatches(quoteId: Int, excerptId: Int?): Op<Boolean> =
    (QuoteTagAssignments.quoteId eq quoteId) and
        (if (excerptId == null) QuoteTagAssignments.excerptId.isNull() else QuoteTagAssignments.excerptId eq excerptId)
