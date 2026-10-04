package no.esotericgames.quotes.server.imagegen

import io.ktor.server.config.ApplicationConfig

data class ComfyUiConfig(
    val baseUrl: String,
    val requestTimeoutMillis: Long,
    val imageTimeoutMillis: Long,
)

/** Output size and sampler steps for one [ImageKind]. Width and height go straight onto the latent image. */
data class ImageRenderSettings(val width: Int, val height: Int, val steps: Int)

data class ImageGenerationConfig(
    val comfyUi: ComfyUiConfig,
    val storageDir: String,
    val background: ImageRenderSettings,
    val motif: ImageRenderSettings,
) {
    fun renderSettings(kind: ImageKind): ImageRenderSettings = when (kind) {
        ImageKind.BACKGROUND -> background
        ImageKind.ELEMENT -> motif
    }
}

val DEFAULT_IMAGE_GENERATION_CONFIG = ImageGenerationConfig(
    comfyUi = ComfyUiConfig(
        baseUrl = "http://localhost:8188",
        requestTimeoutMillis = 30_000,
        imageTimeoutMillis = 900_000,
    ),
    storageDir = "generated-images",
    background = ImageRenderSettings(width = 2752, height = 1536, steps = 40),
    motif = ImageRenderSettings(width = 1024, height = 1024, steps = 40),
)

/**
 * Same typed-config pattern as [no.esotericgames.quotes.server.tagging.loadTaggingConfig], reading the
 * `imageGeneration` HOCON block with in-code fallback defaults, so a test that never loads
 * `application.conf` still works.
 */
fun loadImageGenerationConfig(rootConfig: ApplicationConfig): ImageGenerationConfig {
    val defaults = DEFAULT_IMAGE_GENERATION_CONFIG
    fun string(key: String, default: String) = rootConfig.propertyOrNull(key)?.getString() ?: default
    fun int(key: String, default: Int) = rootConfig.propertyOrNull(key)?.getString()?.toInt() ?: default
    fun long(key: String, default: Long) = rootConfig.propertyOrNull(key)?.getString()?.toLong() ?: default
    fun render(prefix: String, default: ImageRenderSettings) = ImageRenderSettings(
        width = int("$prefix.width", default.width),
        height = int("$prefix.height", default.height),
        steps = int("$prefix.steps", default.steps),
    )

    return ImageGenerationConfig(
        comfyUi = ComfyUiConfig(
            baseUrl = string("imageGeneration.comfyui.baseUrl", defaults.comfyUi.baseUrl).trimEnd('/'),
            requestTimeoutMillis = long("imageGeneration.comfyui.requestTimeoutMillis", defaults.comfyUi.requestTimeoutMillis),
            imageTimeoutMillis = long("imageGeneration.comfyui.imageTimeoutMillis", defaults.comfyUi.imageTimeoutMillis),
        ),
        storageDir = string("imageGeneration.storageDir", defaults.storageDir),
        background = render("imageGeneration.background", defaults.background),
        motif = render("imageGeneration.motif", defaults.motif),
    )
}
