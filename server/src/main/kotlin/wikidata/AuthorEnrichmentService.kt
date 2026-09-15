package no.esotericgames.quotes.server.wikidata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.AuthorEnrichmentResponse
import no.esotericgames.quotes.server.db.Authors
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

private data class AuthorCandidate(val id: Int, val name: String, val birthYear: Int?, val deathYear: Int?)

/**
 * A manually-triggered, idempotent, safe-to-re-run batch action — not tied to import time, since
 * it's a cross-cutting backfill over the whole `authors` table rather than a per-quote concern, and
 * this app has no scheduled-job infrastructure to tie it to. Only ever fills currently-null
 * `wikidata_qid`/`birth_year`/`death_year`; never overwrites a value a human already entered.
 */
class AuthorEnrichmentService(private val client: WikidataClient) {

    suspend fun enrichAuthorsFromWikidata(): AuthorEnrichmentResponse {
        val candidates = withContext(Dispatchers.IO) {
            suspendTransaction {
                Authors.selectAll().where { Authors.wikidataQid.isNull() }.map {
                    AuthorCandidate(
                        id = it[Authors.id],
                        name = it[Authors.name],
                        birthYear = it[Authors.birthYear],
                        deathYear = it[Authors.deathYear],
                    )
                }
            }
        }

        var enrichedCount = 0
        var skippedCount = 0
        for (candidate in candidates) {
            val qid = client.searchEntity(candidate.name)
            if (qid == null) {
                skippedCount++
                continue
            }
            val dates = client.fetchDates(qid)
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    Authors.update({ Authors.id eq candidate.id }) {
                        it[wikidataQid] = qid
                        if (candidate.birthYear == null && dates.birthYear != null) it[birthYear] = dates.birthYear
                        if (candidate.deathYear == null && dates.deathYear != null) it[deathYear] = dates.deathYear
                    }
                }
            }
            enrichedCount++
        }

        return AuthorEnrichmentResponse(checked = candidates.size, enriched = enrichedCount, skipped = skippedCount)
    }
}
