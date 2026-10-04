package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComfyWorkflowTest {

    private val inputs = WorkflowInputs(
        prompt = "a lantern",
        negativePrompt = "text",
        seed = 123_456_789L,
        steps = 12,
        width = 640,
        height = 480,
        filenamePrefix = "tvquotes/element/5",
    )

    @Test
    fun `fills prompt, seed, steps, size and filename prefix into the bundled workflows`() {
        ImageKind.entries.forEach { kind ->
            val workflow = ComfyWorkflow.forKind(kind)
            val filled = workflow.fill(inputs)

            fun input(node: String, name: String) = filled.getValue(node).jsonObject.getValue("inputs").jsonObject.getValue(name).jsonPrimitive

            assertEquals("a lantern", input(ComfyWorkflow.TEXT_ENCODE_NODE, "prompt").content)
            assertEquals("text", input(ComfyWorkflow.TEXT_ENCODE_NODE, "negative_prompt").content)
            assertEquals(123_456_789L, input(ComfyWorkflow.SAMPLER_NODE, "seed").long)
            assertEquals(12, input(ComfyWorkflow.SAMPLER_NODE, "steps").int)
            assertEquals(640, input(ComfyWorkflow.LATENT_NODE, "width").int)
            assertEquals(480, input(ComfyWorkflow.LATENT_NODE, "height").int)
            assertEquals("tvquotes/element/5", input(ComfyWorkflow.SAVE_NODE, "filename_prefix").content)
            // Untouched inputs and links survive.
            assertEquals("euler", input(ComfyWorkflow.SAMPLER_NODE, "sampler_name").content)
            assertEquals("qwen_image_2.1_Q5_K_M.gguf", workflow.modelName)
        }
    }

    @Test
    fun `rejects a workflow missing a required node or with the wrong node type`() {
        val workflowJson = javaClass.getResourceAsStream("/comfyui/qwen-image-2.1-motif-v1.json")!!.bufferedReader().readText()

        assertFailsWith<IllegalArgumentException> {
            ComfyWorkflow.parse("broken", workflowJson.replace("\"461\"", "\"999\""))
        }
        assertFailsWith<IllegalArgumentException> {
            ComfyWorkflow.parse("broken", workflowJson.replace("\"KSampler\"", "\"KSamplerAdvanced\""))
        }
    }
}
