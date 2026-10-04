package no.esotericgames.quotes.server.imagegen

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageGenerationConfigTest {

    @Test
    fun `falls back to in-code defaults when no imageGeneration config is present`() {
        assertEquals(DEFAULT_IMAGE_GENERATION_CONFIG, loadImageGenerationConfig(MapApplicationConfig()))
    }

    @Test
    fun `reads configured values when present`() {
        val rootConfig = MapApplicationConfig(
            "imageGeneration.comfyui.baseUrl" to "http://gpu-box.local:8188/",
            "imageGeneration.comfyui.requestTimeoutMillis" to "5000",
            "imageGeneration.comfyui.imageTimeoutMillis" to "60000",
            "imageGeneration.storageDir" to "/data/images",
            "imageGeneration.background.width" to "1920",
            "imageGeneration.background.height" to "1088",
            "imageGeneration.background.steps" to "20",
            "imageGeneration.motif.width" to "768",
            "imageGeneration.motif.height" to "768",
            "imageGeneration.motif.steps" to "25",
        )

        val config = loadImageGenerationConfig(rootConfig)

        // A trailing slash is trimmed so paths can be appended directly.
        assertEquals(ComfyUiConfig("http://gpu-box.local:8188", 5000L, 60000L), config.comfyUi)
        assertEquals("/data/images", config.storageDir)
        assertEquals(ImageRenderSettings(1920, 1088, 20), config.renderSettings(ImageKind.BACKGROUND))
        assertEquals(ImageRenderSettings(768, 768, 25), config.renderSettings(ImageKind.ELEMENT))
    }
}
