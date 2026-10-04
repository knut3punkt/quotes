package no.esotericgames.quotes.server.imagegen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.TagImages
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max

/** Widths a derivative can have, so the on-disk cache stays bounded however clients ask. */
val DERIVATIVE_WIDTHS = listOf(640, 1280, 1920)

private const val DERIVED_DIRECTORY = "derived"
private const val BACKGROUND_JPEG_QUALITY = 0.88f

/** A file to send for a tag image request, with an [etag] that changes whenever the bytes could. */
data class TagImageFile(val path: Path, val etag: String)

/**
 * Serves stored tag images (docs/features/tag-images.md, "Serving"). A requested width snaps to one of
 * [DERIVATIVE_WIDTHS], and a smaller copy is made once and cached under `<storageDir>/derived/`:
 * backgrounds as JPEG, which is a fraction of the PNG's size, elements as PNG to keep their alpha.
 */
class TagImageFileService(storageDir: String) {

    private val root: Path = Path.of(storageDir).toAbsolutePath().normalize()

    suspend fun imageFile(imageId: Int, requestedWidth: Int?): TagImageFile = withContext(Dispatchers.IO) {
        val row = suspendTransaction {
            TagImages.selectAll().where { TagImages.id eq imageId }.singleOrNull()
        } ?: throw NoSuchElementException("tag image $imageId not found")
        val original = resolveInside(root, row[TagImages.filePath])
        if (!Files.isRegularFile(original)) throw NoSuchElementException("file for tag image $imageId is missing")

        val sha256 = row[TagImages.sha256]
        val width = snapDerivativeWidth(requestedWidth, row[TagImages.width])
            ?: return@withContext TagImageFile(original, sha256)
        val isBackground = row[TagImages.kind] == ImageKind.BACKGROUND.dbValue
        val derived = root.resolve(DERIVED_DIRECTORY).resolve("$imageId-$width.${if (isBackground) "jpg" else "png"}")
        if (!Files.isRegularFile(derived)) writeDerivative(original, derived, width, isBackground)
        TagImageFile(derived, "$sha256-$width")
    }

    private fun writeDerivative(original: Path, target: Path, width: Int, asJpeg: Boolean) {
        val source = ImageIO.read(original.toFile()) ?: error("cannot decode ${original.fileName}")
        val resized = resizeToWidth(source, width, keepAlpha = !asJpeg)
        Files.createDirectories(target.parent)
        // Two requests may make the same derivative at once; each writes its own temp file, and the
        // atomic move means a reader only ever sees a complete one.
        val temp = Files.createTempFile(target.parent, ".partial-", if (asJpeg) ".jpg" else ".png")
        try {
            if (asJpeg) writeJpeg(resized, temp) else ImageIO.write(resized, "png", temp.toFile())
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun writeJpeg(image: BufferedImage, target: Path) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(target.toFile()).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = BACKGROUND_JPEG_QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
    }
}

/**
 * The derivative width to serve for [requested], or null for the original: when no width is asked for,
 * or when the snapped width would not be smaller than the original.
 */
fun snapDerivativeWidth(requested: Int?, originalWidth: Int): Int? {
    if (requested == null || requested <= 0) return null
    val snapped = DERIVATIVE_WIDTHS.firstOrNull { it >= requested } ?: DERIVATIVE_WIDTHS.last()
    return snapped.takeIf { it < originalWidth }
}

/** Resolves [relativePath] under [root], refusing anything that would land outside it. */
fun resolveInside(root: Path, relativePath: String): Path {
    val resolved = root.resolve(relativePath).normalize()
    require(resolved.startsWith(root) && resolved != root) { "image path escapes the storage directory" }
    return resolved
}

/**
 * Scales [source] to [targetWidth], keeping its aspect ratio. Halving step by step with bicubic
 * interpolation avoids the aliasing a single large bicubic step gives.
 */
fun resizeToWidth(source: BufferedImage, targetWidth: Int, keepAlpha: Boolean): BufferedImage {
    val targetHeight = max(1, Math.round(source.height.toDouble() * targetWidth / source.width).toInt())
    val type = if (keepAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    var current = source
    var width = source.width
    var height = source.height
    do {
        width = max(targetWidth, width / 2)
        height = max(targetHeight, height / 2)
        val next = BufferedImage(width, height, type)
        val graphics = next.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(current, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        current = next
    } while (width != targetWidth || height != targetHeight)
    return current
}
