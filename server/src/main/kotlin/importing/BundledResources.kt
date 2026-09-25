package no.esotericgames.quotes.server.importing

import kotlinx.serialization.json.Json

private val bundledResourceJson = Json { ignoreUnknownKeys = true }

internal class BundledResourceAnchor

/**
 * Reads and JSON-decodes a resource bundled on the classpath at [resourcePath] (an absolute path,
 * e.g. "/scripture/bible-kjv-seed-refs.json"). Used by importers backed by a fixed, hand-curated or
 * vendored file (a Bible/Quran seed list, the Tao Te Ching translation) rather than a live fetch, so
 * they don't depend on an external service being reachable to import something that never changes.
 */
internal inline fun <reified T> loadBundledJsonResource(resourcePath: String): T {
    val stream = BundledResourceAnchor::class.java.getResourceAsStream(resourcePath)
        ?: error("bundled resource not found: $resourcePath")
    val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    return bundledResourceJson.decodeFromString(content)
}
