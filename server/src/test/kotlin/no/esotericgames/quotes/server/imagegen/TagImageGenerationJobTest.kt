package no.esotericgames.quotes.server.imagegen

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.TagImages
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiClient
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiEvent
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiResult
import no.esotericgames.quotes.server.imagegen.comfyui.OutputImageRef
import no.esotericgames.quotes.server.imagegen.comfyui.PromptCompletion
import no.esotericgames.quotes.server.loadDotEnvIntoSystemProperties
import no.esotericgames.quotes.server.tagging.ORIGIN_LLM
import no.esotericgames.quotes.server.tagging.TagBreadth
import no.esotericgames.quotes.server.tagging.TagFacet
import no.esotericgames.quotes.server.tagging.TagNormalization
import no.esotericgames.quotes.server.tagging.TagVocabulary
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Stands in for ComfyUI: each submitted prompt gets an id, emits start/progress events, and completes
 * on the second history poll with whatever [completionFor] says for the prompt's text.
 */
private class FakeComfyUiClient(
    private val completionFor: (prompt: String) -> PromptCompletion?,
    private val imageBytes: ByteArray,
) : ComfyUiClient {
    private val counter = AtomicInteger()
    private val promptTexts = mutableMapOf<String, String>()
    private val polls = mutableMapOf<String, Int>()
    private val eventFlow = MutableSharedFlow<ComfyUiEvent>(replay = 16, extraBufferCapacity = 64)
    val cancelled = mutableListOf<String>()

    override suspend fun submitPrompt(workflow: JsonObject, clientId: String): ComfyUiResult<String> {
        val promptId = "p${counter.incrementAndGet()}"
        promptTexts[promptId] = workflow.getValue(ComfyWorkflow.TEXT_ENCODE_NODE).jsonObject
            .getValue("inputs").jsonObject.getValue("prompt").jsonPrimitive.content
        eventFlow.emit(ComfyUiEvent.ExecutionStart(promptId))
        eventFlow.emit(ComfyUiEvent.Progress(promptId, 3, 40))
        return ComfyUiResult.Success(promptId)
    }

    override suspend fun fetchCompletion(promptId: String, saveNodeId: String): ComfyUiResult<PromptCompletion?> {
        val pollCount = polls.merge(promptId, 1, Int::plus)!!
        return ComfyUiResult.Success(if (pollCount < 2) null else completionFor(promptTexts.getValue(promptId)))
    }

    override suspend fun downloadImage(image: OutputImageRef): ComfyUiResult<ByteArray> = ComfyUiResult.Success(imageBytes)

    override suspend fun cancelPrompt(promptId: String) {
        cancelled += promptId
    }

    override fun events(clientId: String): Flow<ComfyUiEvent> = eventFlow
}

class TagImageGenerationJobTest {

    // Like QuoteTaggingServiceTest, this needs the local dev Postgres. Tag names carry a random prefix,
    // and the job only ever sees this test's tags, so real tags never get (fake) images.
    @Test
    fun `generates images for used mood and motif tags, isolates failures, and supports cancel`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        client.get("/health") // starts the application, and with it the DB connection and migrations

        val prefix = "zzi" + UUID.randomUUID().toString().replace("-", "").take(10)
        val storageDir = Files.createTempDirectory("tag-images-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val quoteId = insertQuote()

        try {
            val calmId = createTag(TagFacet.MOOD, "$prefix calm")
            val lampId = createTag(TagFacet.MOTIF, "$prefix lamp")
            val conceptId = createTag(TagFacet.CONCEPT, "$prefix idea")
            val unusedMoodId = createTag(TagFacet.MOOD, "$prefix unused")
            val rejectedMotifId = createTag(TagFacet.MOTIF, "$prefix rejected")
            assign(quoteId, calmId)
            assign(quoteId, lampId)
            assign(quoteId, conceptId)
            assign(quoteId, rejectedMotifId, rejected = true)
            val ownTags: suspend () -> List<EligibleTag> = {
                withContext(Dispatchers.IO) {
                    suspendTransaction { TagImageGenerationJob.findEligibleTags().filter { it.name.startsWith(prefix) } }
                }
            }

            // Only used, canonical mood and motif tags qualify; moods come first.
            assertEquals(listOf(calmId to ImageKind.BACKGROUND, lampId to ImageKind.ELEMENT), ownTags().map { it.id to it.kind })
            assertTrue(unusedMoodId !in ownTags().map { it.id })

            val fake = FakeComfyUiClient(
                completionFor = { prompt ->
                    if ("calm" in prompt) {
                        PromptCompletion.Failed("KSampler: out of memory")
                    } else {
                        PromptCompletion.Succeeded(listOf(OutputImageRef("x.png", "", "output")))
                    }
                },
                imageBytes = transparentElementPng(),
            )
            val config = DEFAULT_IMAGE_GENERATION_CONFIG.copy(storageDir = storageDir.toString())
            val job = TagImageGenerationJob(fake, config, scope, historyPollMillis = 10, nextSeed = { 4242L }, loadEligibleTags = ownTags)

            val initial = job.start()
            assertEquals(listOf("queued", "queued"), initial.items.map { it.status })
            val finished = withTimeout(10_000) { job.state.first { it.status == "finished" } }

            val (calmItem, lampItem) = finished.items
            assertEquals("failed", calmItem.status)
            assertEquals("KSampler: out of memory", calmItem.error)
            assertEquals("done", lampItem.status)
            assertEquals(4242L, lampItem.seed)
            assertTrue(lampItem.prompt!!.contains("$prefix lamp"))

            val row = withContext(Dispatchers.IO) {
                suspendTransaction { TagImages.selectAll().where { TagImages.tagId eq lampId }.single() }
            }
            assertEquals("element", row[TagImages.kind])
            assertEquals(lampItem.filePath, row[TagImages.filePath])
            assertTrue(row[TagImages.hasAlpha])
            assertEquals(4242L, row[TagImages.seed])
            assertEquals("motif-v1", row[TagImages.promptRecipeVersion])
            assertEquals("p2", row[TagImages.comfyPromptId])
            assertTrue(storageDir.resolve(row[TagImages.filePath]).exists())

            // The failed mood is still missing, so a second run retries only that one; this time it never
            // completes, which also shows a run can't be started twice and can be cancelled.
            val stuck = FakeComfyUiClient(completionFor = { null }, imageBytes = transparentElementPng())
            val stuckJob = TagImageGenerationJob(stuck, config, scope, historyPollMillis = 10, loadEligibleTags = ownTags)
            assertEquals(listOf(calmId), stuckJob.start().items.map { it.tagId })
            withTimeout(5_000) { stuckJob.state.first { it.items.single().status == "running" } }
            assertFailsWith<IllegalStateException> { stuckJob.start() }

            stuckJob.cancel()
            val cancelled = withTimeout(10_000) { stuckJob.state.first { it.status == "cancelled" } }
            assertEquals("cancelled", cancelled.items.single().status)
            assertEquals(listOf("p1"), stuck.cancelled)
        } finally {
            scope.cancel()
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    Quotes.deleteWhere { Quotes.id eq quoteId } // cascades to assignments
                    Tags.deleteWhere { Tags.normalizedName like "$prefix%" } // cascades to tag_images
                }
            }
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            storageDir.deleteRecursively()
        }
    }

    private suspend fun insertQuote(): Int = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.insert {
                it[text] = "A test quote for tag images."
                it[normalizedText] = "a test quote for tag images."
            }[Quotes.id]
        }
    }

    private suspend fun createTag(facet: TagFacet, name: String): Int = withContext(Dispatchers.IO) {
        suspendTransaction {
            val normalized = TagNormalization.normalize(name, facet)!!
            TagVocabulary.resolveOrCreate(facet, normalized, if (facet == TagFacet.CONCEPT) TagBreadth.BROAD else null, ORIGIN_LLM)
        }
    }

    private suspend fun assign(quoteId: Int, tagId: Int, rejected: Boolean = false) = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteTagAssignments.insert {
                it[QuoteTagAssignments.quoteId] = quoteId
                it[QuoteTagAssignments.tagId] = tagId
                it[origin] = ORIGIN_LLM
                it[relevance] = 2
                it[basis] = "text"
                it[QuoteTagAssignments.rejected] = rejected
            }
        }
    }

    private fun transparentElementPng(): ByteArray {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 8 until 24) for (x in 8 until 24) image.setRGB(x, y, 0xFF336699.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
