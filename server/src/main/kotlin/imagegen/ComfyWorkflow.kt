package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The values the server fills into a workflow for one image. */
data class WorkflowInputs(
    val prompt: String,
    val negativePrompt: String,
    val seed: Long,
    val steps: Int,
    val width: Int,
    val height: Int,
    val filenamePrefix: String,
)

/**
 * A ComfyUI API-format workflow (as exported with "Export (API)"), kept as a version-controlled resource
 * under `resources/comfyui/`. The server never builds a graph itself: it only fills the inputs of the
 * nodes listed in [REQUIRED_NODES], and refuses at load time a workflow that lacks one of them, so an
 * edited export that renumbered its nodes fails fast instead of silently generating with stale inputs.
 *
 * When a workflow changes, add a new resource file, keep the old one, and bump the version passed to
 * [load]; each stored image records the version it was generated with.
 */
class ComfyWorkflow private constructor(val version: String, private val template: JsonObject) {

    /** The diffusion model file the workflow loads, recorded on each image for provenance. */
    val modelName: String? = template[UNET_LOADER_NODE]?.inputs()?.get("unet_name")?.jsonPrimitive?.contentOrNull

    fun fill(inputs: WorkflowInputs): JsonObject {
        val nodes = template.toMutableMap()
        fun setInputs(nodeId: String, values: Map<String, JsonPrimitive>) {
            val node = nodes.getValue(nodeId).jsonObject
            val newInputs = JsonObject(node.inputs() + values)
            nodes[nodeId] = JsonObject(node + ("inputs" to newInputs))
        }
        setInputs(
            TEXT_ENCODE_NODE,
            mapOf("prompt" to JsonPrimitive(inputs.prompt), "negative_prompt" to JsonPrimitive(inputs.negativePrompt)),
        )
        setInputs(SAMPLER_NODE, mapOf("seed" to JsonPrimitive(inputs.seed), "steps" to JsonPrimitive(inputs.steps)))
        setInputs(LATENT_NODE, mapOf("width" to JsonPrimitive(inputs.width), "height" to JsonPrimitive(inputs.height)))
        setInputs(SAVE_NODE, mapOf("filename_prefix" to JsonPrimitive(inputs.filenamePrefix)))
        return JsonObject(nodes)
    }

    companion object {
        const val TEXT_ENCODE_NODE = "459:452"
        const val SAMPLER_NODE = "459:458"
        const val LATENT_NODE = "459:456"
        const val SAVE_NODE = "461"
        private const val UNET_LOADER_NODE = "459:451"

        private val REQUIRED_NODES = mapOf(
            TEXT_ENCODE_NODE to "TextEncodeQwenImage21",
            SAMPLER_NODE to "KSampler",
            LATENT_NODE to "EmptyLatentImage",
            SAVE_NODE to "SaveImageAdvanced",
        )

        private val BUNDLED = mapOf(
            ImageKind.BACKGROUND to ("qwen-image-2.1-background-v1" to "/comfyui/qwen-image-2.1-background-v1.json"),
            ImageKind.ELEMENT to ("qwen-image-2.1-motif-v1" to "/comfyui/qwen-image-2.1-motif-v1.json"),
        )

        fun forKind(kind: ImageKind): ComfyWorkflow {
            val (version, resourcePath) = BUNDLED.getValue(kind)
            val stream = ComfyWorkflow::class.java.getResourceAsStream(resourcePath)
                ?: error("bundled resource not found: $resourcePath")
            return parse(version, stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        fun parse(version: String, json: String): ComfyWorkflow {
            val template = Json.parseToJsonElement(json).jsonObject
            REQUIRED_NODES.forEach { (nodeId, classType) ->
                val node = template[nodeId] as? JsonObject
                    ?: throw IllegalArgumentException("workflow $version has no node $nodeId ($classType)")
                val actual = node["class_type"]?.jsonPrimitive?.contentOrNull
                require(actual == classType) { "workflow $version node $nodeId is $actual, expected $classType" }
                require(node["inputs"] is JsonObject) { "workflow $version node $nodeId has no inputs" }
            }
            return ComfyWorkflow(version, template)
        }

        private fun JsonElement.inputs(): JsonObject = jsonObject.getValue("inputs").jsonObject
    }
}
