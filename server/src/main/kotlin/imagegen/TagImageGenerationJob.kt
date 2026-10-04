package no.esotericgames.quotes.server.imagegen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import no.esotericgames.quotes.server.admin.ImageGenerationItem
import no.esotericgames.quotes.server.admin.ImageGenerationJobState
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.TagImages
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiClient
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiEvent
import no.esotericgames.quotes.server.imagegen.comfyui.ComfyUiResult
import no.esotericgames.quotes.server.imagegen.comfyui.PromptCompletion
import no.esotericgames.quotes.server.tagging.TagFacet
import no.esotericgames.quotes.server.tagging.TagVocabulary
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

private const val ERROR_MESSAGE_MAX_LENGTH = 500
private const val SLUG_MAX_LENGTH = 40
private const val EVENTS_RECONNECT_DELAY_MILLIS = 3_000L

/** JavaScript numbers are exact up to 2^53, and the admin shows the seed, so seeds stay below it. */
private const val MAX_SEED_EXCLUSIVE = 1L shl 53

/** A tag that should get an image in this run. */
data class EligibleTag(val id: Int, val name: String, val normalizedName: String, val kind: ImageKind)

/**
 * Generates one image per mood and motif tag that has none yet (docs/features/tag-images.md), one at a
 * time on ComfyUI, in a coroutine owned by [scope] so the admin request that starts it returns at once.
 *
 * Only one run exists at a time. Its [state] is the single source of truth for the admin: items move
 * through their statuses as the run submits each prompt, ComfyUI's WebSocket reports execution and
 * sampler steps, and the finished PNG is analysed, written to disk and recorded in `tag_images`.
 * Completion is decided by polling `/history`, so a dropped WebSocket only costs the step counts. Each
 * item is processed independently: a failure marks that item and the run moves on. Failures leave no row,
 * so the next run retries them. The state is in memory only; a server restart forgets the last run,
 * and the next run simply picks up the tags that still have no image.
 */
class TagImageGenerationJob(
    private val client: ComfyUiClient,
    private val config: ImageGenerationConfig,
    private val scope: CoroutineScope,
    private val historyPollMillis: Long = 1_000,
    private val nextSeed: () -> Long = { Random.nextLong(0, MAX_SEED_EXCLUSIVE) },
    // Lets the dev-database test run on its own tags instead of every eligible tag in the vocabulary.
    private val loadEligibleTags: suspend () -> List<EligibleTag> = {
        withContext(Dispatchers.IO) { suspendTransaction { findEligibleTags() } }
    },
) {
    private val logger = LoggerFactory.getLogger(TagImageGenerationJob::class.java)
    private val json = Json { encodeDefaults = true }

    private val mutableState = MutableStateFlow(ImageGenerationJobState(status = "idle", comfyUiBaseUrl = config.comfyUi.baseUrl))
    val state: StateFlow<ImageGenerationJobState> = mutableState.asStateFlow()

    private val recipes = ImageKind.entries.associateWith { PromptRecipe.forKind(it) }
    private val workflows = ImageKind.entries.associateWith { ComfyWorkflow.forKind(it) }

    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    private var currentPromptId: String? = null

    /** prompt_id -> item index, so WebSocket events can be matched to their item. */
    private val itemIndexByPromptId = ConcurrentHashMap<String, Int>()

    /**
     * Starts a run over every eligible tag and returns the initial state. Throws [IllegalStateException]
     * (mapped to 409) while a run is in progress.
     */
    suspend fun start(): ImageGenerationJobState {
        check(running.compareAndSet(false, true)) { "an image generation run is already in progress" }
        try {
            val tags = loadEligibleTags()
            cancelRequested.set(false)
            itemIndexByPromptId.clear()
            val now = System.currentTimeMillis()
            mutableState.value = ImageGenerationJobState(
                status = if (tags.isEmpty()) "finished" else "running",
                comfyUiBaseUrl = config.comfyUi.baseUrl,
                startedAt = now,
                finishedAt = if (tags.isEmpty()) now else null,
                items = tags.map { tag ->
                    ImageGenerationItem(
                        tagId = tag.id,
                        tagName = tag.name,
                        facet = tag.kind.facet.dbValue,
                        kind = tag.kind.dbValue,
                        status = "queued",
                    )
                },
            )
            if (tags.isEmpty()) {
                running.set(false)
            } else {
                scope.launch { runAll(tags) }
            }
            return mutableState.value
        } catch (e: Throwable) {
            running.set(false)
            throw e
        }
    }

    /** Stops the run after cancelling the prompt currently in ComfyUI. Does nothing when no run is active. */
    suspend fun cancel(): ImageGenerationJobState {
        if (!running.get() || !cancelRequested.compareAndSet(false, true)) return mutableState.value
        mutableState.update { it.copy(status = "cancelling") }
        currentPromptId?.let { client.cancelPrompt(it) }
        return mutableState.value
    }

    private suspend fun runAll(tags: List<EligibleTag>) {
        val clientId = UUID.randomUUID().toString()
        try {
            coroutineScope {
                val eventsJob = launch { followEvents(clientId) }
                try {
                    for ((index, tag) in tags.withIndex()) {
                        if (cancelRequested.get()) break
                        processItem(index, tag, clientId)
                    }
                } finally {
                    eventsJob.cancelAndJoin()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("image generation run aborted", e)
        } finally {
            val cancelled = cancelRequested.get()
            mutableState.update { state ->
                state.copy(
                    status = if (cancelled) "cancelled" else "finished",
                    finishedAt = System.currentTimeMillis(),
                    items = state.items.map { item ->
                        if (item.status in FINAL_STATUSES) item else item.copy(status = "cancelled", step = null)
                    },
                )
            }
            currentPromptId = null
            running.set(false)
        }
    }

    private suspend fun processItem(index: Int, tag: EligibleTag, clientId: String) {
        val startedAt = System.currentTimeMillis()
        try {
            val seed = nextSeed()
            val recipe = recipes.getValue(tag.kind)
            val workflow = workflows.getValue(tag.kind)
            val settings = config.renderSettings(tag.kind)
            val composed = recipe.compose(tag.name, seed)
            updateItem(index) {
                it.copy(status = "submitting", prompt = composed.prompt, seed = seed, maxSteps = settings.steps, startedAt = startedAt)
            }

            val filled = workflow.fill(
                WorkflowInputs(
                    prompt = composed.prompt,
                    negativePrompt = composed.negativePrompt,
                    seed = seed,
                    steps = settings.steps,
                    width = settings.width,
                    height = settings.height,
                    filenamePrefix = "tvquotes/${tag.kind.dbValue}/${tag.id}",
                ),
            )
            val promptId = when (val submitted = client.submitPrompt(filled, clientId)) {
                is ComfyUiResult.Success -> submitted.value
                is ComfyUiResult.Failure -> return failItem(index, submitted.message)
            }
            itemIndexByPromptId[promptId] = index
            currentPromptId = promptId
            // The WebSocket may already have reported this prompt starting, so don't regress its status.
            updateItem(index) { if (it.status == "submitting") it.copy(status = "waiting") else it }
            // A cancel that landed between submitting and recording the id would have missed this prompt.
            if (cancelRequested.get()) client.cancelPrompt(promptId)

            val completion = awaitCompletion(promptId)
            currentPromptId = null
            val images = when (completion) {
                is PromptCompletion.Succeeded -> completion.images
                is PromptCompletion.Failed -> {
                    return if (cancelRequested.get()) cancelItem(index) else failItem(index, completion.message)
                }
                null -> return if (cancelRequested.get()) cancelItem(index) else failItem(index, "timed out waiting for ComfyUI")
            }

            updateItem(index) { it.copy(status = "saving", step = it.maxSteps) }
            val bytes = when (val downloaded = client.downloadImage(images.first())) {
                is ComfyUiResult.Success -> downloaded.value
                is ComfyUiResult.Failure -> return failItem(index, downloaded.message)
            }
            val generationMillis = System.currentTimeMillis() - startedAt
            val analysis = withContext(Dispatchers.Default) { ImageAnalysis.analyze(bytes, tag.kind) }
            val relativePath = "${tag.kind.dbValue}/${tag.id}-${slugOf(tag.normalizedName)}-$seed.png"
            val file = writeImageFile(relativePath, bytes)
            try {
                withContext(Dispatchers.IO) {
                    suspendTransaction {
                        insertImageRow(
                            tag, relativePath, bytes, analysis, composed, recipe, workflow, settings, seed, promptId,
                            generationMillis,
                        )
                    }
                }
            } catch (e: Exception) {
                Files.deleteIfExists(file)
                throw e
            }
            updateItem(index) {
                it.copy(status = "done", filePath = relativePath, finishedAt = System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Last-resort guard: one broken image must never abort the rest of the run.
            logger.warn("image generation failed for tag ${tag.id} (${tag.name})", e)
            failItem(index, e.message ?: e::class.simpleName ?: "unknown error")
        }
    }

    /** Polls `/history` until the prompt completes, the per-image timeout passes (`null`), or a cancel lands. */
    private suspend fun awaitCompletion(promptId: String): PromptCompletion? {
        val deadline = System.currentTimeMillis() + config.comfyUi.imageTimeoutMillis
        var cancelSeenAt: Long? = null
        while (System.currentTimeMillis() < deadline) {
            when (val result = client.fetchCompletion(promptId, ComfyWorkflow.SAVE_NODE)) {
                is ComfyUiResult.Success -> result.value?.let { return it }
                // A transient HTTP failure is retried until the deadline; the item records the last one.
                is ComfyUiResult.Failure -> logger.debug("history poll for {} failed: {}", promptId, result.message)
            }
            if (cancelRequested.get()) {
                // A prompt removed from ComfyUI's pending queue never shows up in history, so give it a
                // few polls to report an interrupted run and then stop waiting.
                val seenAt = cancelSeenAt ?: System.currentTimeMillis().also { cancelSeenAt = it }
                if (System.currentTimeMillis() - seenAt > CANCEL_GRACE_MILLIS) return PromptCompletion.Failed("cancelled")
            }
            delay(historyPollMillis)
        }
        return null
    }

    /** Follows ComfyUI's WebSocket for the whole run, reconnecting after a drop. */
    private suspend fun followEvents(clientId: String) = coroutineScope {
        while (isActive) {
            try {
                client.events(clientId).collect { event ->
                    mutableState.update { if (it.connectionWarning != null) it.copy(connectionWarning = null) else it }
                    handleEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(connectionWarning = "ComfyUI WebSocket unavailable (${e.message ?: e::class.simpleName}); polling for results")
                }
            }
            delay(EVENTS_RECONNECT_DELAY_MILLIS)
        }
    }

    private fun handleEvent(event: ComfyUiEvent) {
        when (event) {
            is ComfyUiEvent.QueueStatus -> mutableState.update { it.copy(comfyQueueRemaining = event.queueRemaining) }
            is ComfyUiEvent.ExecutionStart -> updateActiveItem(event.promptId) { it.copy(status = "running", step = 0) }
            is ComfyUiEvent.Progress -> updateActiveItem(event.promptId) {
                it.copy(status = "running", step = event.value, maxSteps = event.max)
            }
            // Completion and errors are read from /history, which also covers a dropped WebSocket.
            else -> Unit
        }
    }

    /** Applies a WebSocket update to the prompt's item, unless that item has already moved past ComfyUI. */
    private fun updateActiveItem(promptId: String, transform: (ImageGenerationItem) -> ImageGenerationItem) {
        val index = itemIndexByPromptId[promptId] ?: return
        updateItem(index) { item -> if (item.status in ACTIVE_STATUSES) transform(item) else item }
    }

    private fun updateItem(index: Int, transform: (ImageGenerationItem) -> ImageGenerationItem) {
        mutableState.update { state ->
            state.copy(items = state.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
        }
    }

    private fun failItem(index: Int, message: String) {
        updateItem(index) {
            it.copy(status = "failed", error = message.take(ERROR_MESSAGE_MAX_LENGTH), finishedAt = System.currentTimeMillis())
        }
    }

    private fun cancelItem(index: Int) {
        updateItem(index) { it.copy(status = "cancelled", finishedAt = System.currentTimeMillis()) }
    }

    private suspend fun writeImageFile(relativePath: String, bytes: ByteArray): Path = withContext(Dispatchers.IO) {
        val target = Path.of(config.storageDir).resolve(relativePath).toAbsolutePath()
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".partial-", ".png")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
        target
    }

    private fun insertImageRow(
        tag: EligibleTag,
        relativePath: String,
        bytes: ByteArray,
        analysis: ImageAnalysisResult,
        composed: ComposedPrompt,
        recipe: PromptRecipe,
        workflow: ComfyWorkflow,
        settings: ImageRenderSettings,
        seed: Long,
        promptId: String,
        generationMillis: Long,
    ) {
        // An admin may have merged the tag while its image was generating; the image follows the merge.
        val tagId = TagVocabulary.canonicalId(tag.id)
        TagImages.insert {
            it[TagImages.tagId] = tagId
            it[kind] = tag.kind.dbValue
            it[filePath] = relativePath
            it[width] = analysis.width
            it[height] = analysis.height
            it[fileSizeBytes] = bytes.size.toLong()
            it[sha256] = sha256Hex(bytes)
            it[hasAlpha] = analysis.hasAlpha
            it[transparentFraction] = analysis.transparentFraction.toFloat()
            it[meanLuminance] = analysis.meanLuminance.toFloat()
            it[dominantColors] = json.encodeToJsonElement(analysis.dominantColors)
            it[layout] = analysis.layout
            it[prompt] = composed.prompt
            it[negativePrompt] = composed.negativePrompt
            it[promptRecipeVersion] = recipe.version
            it[promptIngredients] = JsonObject(composed.ingredients.mapValues { (_, phrase) -> JsonPrimitive(phrase) })
            it[TagImages.seed] = seed
            it[workflowVersion] = workflow.version
            it[modelName] = workflow.modelName
            it[steps] = settings.steps
            it[comfyPromptId] = promptId
            it[TagImages.generationMillis] = generationMillis.toInt()
        }
    }

    companion object {
        private const val CANCEL_GRACE_MILLIS = 5_000L
        private val FINAL_STATUSES = setOf("done", "failed", "cancelled")
        private val ACTIVE_STATUSES = setOf("submitting", "waiting", "running")

        /**
         * Canonical mood and motif tags in use (at least one non-rejected assignment) that have no image yet.
         * Moods come first, then motifs, each most-used first, so an interrupted run has covered the tags
         * that matter most.
         */
        fun findEligibleTags(): List<EligibleTag> {
            val facets = ImageKind.entries.map { it.facet.dbValue }
            val usage = QuoteTagAssignments.select(QuoteTagAssignments.tagId)
                .where { QuoteTagAssignments.rejected eq false }
                .groupingBy { it[QuoteTagAssignments.tagId] }
                .eachCount()
            val withImages = TagImages.select(TagImages.tagId).map { it[TagImages.tagId] }.toSet()
            return Tags.selectAll()
                .where { (Tags.facet inList facets) and Tags.mergedIntoId.isNull() }
                .mapNotNull { row ->
                    val id = row[Tags.id]
                    val kind = TagFacet.fromDbValue(row[Tags.facet])?.let { ImageKind.forFacet(it) }
                    if (kind == null || id in withImages || (usage[id] ?: 0) == 0) return@mapNotNull null
                    EligibleTag(id, row[Tags.name], row[Tags.normalizedName], kind)
                }
                .sortedWith(compareBy<EligibleTag> { it.kind.ordinal }.thenByDescending { usage[it.id] ?: 0 }.thenBy { it.name })
        }

        internal fun slugOf(normalizedName: String): String =
            normalizedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(SLUG_MAX_LENGTH).trim('-')
                .ifEmpty { "tag" }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
