package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A rectangle in 0-1 coordinates relative to the image's width and height, origin top left. */
@Serializable
data class NormalizedRect(val x: Double, val y: Double, val width: Double, val height: Double)

@Serializable
data class DominantColor(val hex: String, val weight: Double)

/** One cell of a background's layout grid. */
@Serializable
data class LayoutCell(val luminance: Double, val detail: Double)

/**
 * Layout metadata for a background, for a future collage algorithm: where it is calm enough for the
 * quotation or a collage element, and whether text over the calm area should be light or dark.
 * [cells] is row-major: `cells[row][column]`.
 */
@Serializable
data class BackgroundLayout(
    val gridColumns: Int,
    val gridRows: Int,
    val cells: List<List<LayoutCell>>,
    val calmRegion: NormalizedRect?,
    val textTone: String,
)

/** Layout metadata for a collage element: the box around its visible pixels, for scaling and anchoring. */
@Serializable
data class ElementLayout(val contentBox: NormalizedRect?)

data class ImageAnalysisResult(
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val transparentFraction: Double,
    val meanLuminance: Double,
    val dominantColors: List<DominantColor>,
    /** A [BackgroundLayout] or an [ElementLayout], by kind, as stored in the `layout` JSONB column. */
    val layout: JsonElement,
)

/**
 * Deterministic analysis of a generated PNG, computed once at save time and stored with the image
 * (docs/features/tag-images.md, "Layout metadata"). Luminance is WCAG relative luminance (linearised
 * sRGB, 0-1); detail is the mean luminance gradient of a downscaled copy, so film grain and paper texture
 * barely register while edges and objects do.
 */
object ImageAnalysis {
    const val GRID_COLUMNS = 16
    const val GRID_ROWS = 9

    /** Text over a region brighter than this reads better dark (the WCAG contrast crossover point). */
    private const val TEXT_TONE_LUMINANCE_THRESHOLD = 0.179

    /** A cell this smooth is calm on any image, even one where every cell is that smooth. */
    private const val ABSOLUTE_CALM_DETAIL = 0.02
    private const val OPAQUE_ALPHA = 128
    private const val TRANSPARENT_ALPHA = 16
    private const val DETAIL_SAMPLE_WIDTH = 320
    private const val DOMINANT_COLOR_COUNT = 5
    private const val COLOR_SAMPLE_TARGET = 200_000

    private val json = Json { encodeDefaults = true }

    fun analyze(pngBytes: ByteArray, kind: ImageKind): ImageAnalysisResult {
        val image = ImageIO.read(ByteArrayInputStream(pngBytes))
            ?: throw IllegalArgumentException("not a decodable image")
        return analyze(image, kind)
    }

    fun analyze(image: BufferedImage, kind: ImageKind): ImageAnalysisResult {
        val width = image.width
        val height = image.height
        val argb = image.getRGB(0, 0, width, height, null, 0, width)
        val hasAlpha = image.colorModel.hasAlpha()

        var transparentCount = 0
        var opaqueCount = 0
        var luminanceSum = 0.0
        val luminance = FloatArray(argb.size)
        for (i in argb.indices) {
            val pixel = argb[i]
            val alpha = if (hasAlpha) pixel ushr 24 else 255
            if (alpha < TRANSPARENT_ALPHA) transparentCount++
            val value = relativeLuminance(pixel)
            luminance[i] = value.toFloat()
            if (alpha >= OPAQUE_ALPHA) {
                opaqueCount++
                luminanceSum += value
            }
        }

        val layout = when (kind) {
            ImageKind.BACKGROUND -> json.encodeToJsonElement(backgroundLayout(luminance, width, height))
            ImageKind.ELEMENT -> json.encodeToJsonElement(ElementLayout(contentBox(argb, width, height, hasAlpha)))
        }

        return ImageAnalysisResult(
            width = width,
            height = height,
            hasAlpha = hasAlpha,
            transparentFraction = round3(transparentCount.toDouble() / argb.size),
            meanLuminance = round3(if (opaqueCount == 0) 0.0 else luminanceSum / opaqueCount),
            dominantColors = dominantColors(argb, hasAlpha),
            layout = layout,
        )
    }

    private fun backgroundLayout(luminance: FloatArray, width: Int, height: Int): BackgroundLayout {
        val cellLuminance = Array(GRID_ROWS) { DoubleArray(GRID_COLUMNS) }
        val cellCounts = Array(GRID_ROWS) { IntArray(GRID_COLUMNS) }
        for (y in 0 until height) {
            val row = y * GRID_ROWS / height
            for (x in 0 until width) {
                val column = x * GRID_COLUMNS / width
                cellLuminance[row][column] += luminance[y * width + x]
                cellCounts[row][column]++
            }
        }

        val detail = cellDetail(luminance, width, height)
        val cells = List(GRID_ROWS) { row ->
            List(GRID_COLUMNS) { column ->
                LayoutCell(
                    luminance = round3(cellLuminance[row][column] / max(1, cellCounts[row][column])),
                    detail = round3(detail[row][column]),
                )
            }
        }

        // Calm = at or below the median detail, or smooth in absolute terms; the median keeps a busy
        // image from having no calm area at all, the absolute floor keeps a smooth one fully calm.
        val median = cells.flatten().map { it.detail }.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
        val calmThreshold = max(median, ABSOLUTE_CALM_DETAIL)
        val calm = Array(GRID_ROWS) { row -> BooleanArray(GRID_COLUMNS) { column -> cells[row][column].detail <= calmThreshold } }
        val region = largestTrueRectangle(calm)

        val regionLuminance = region?.let { (left, top, right, bottom) ->
            var sum = 0.0
            var weight = 0
            for (row in top..bottom) for (column in left..right) {
                sum += cellLuminance[row][column]
                weight += cellCounts[row][column]
            }
            sum / max(1, weight)
        } ?: (cellLuminance.sumOf { it.sum() } / (width.toDouble() * height))

        return BackgroundLayout(
            gridColumns = GRID_COLUMNS,
            gridRows = GRID_ROWS,
            cells = cells,
            calmRegion = region?.let { (left, top, right, bottom) ->
                NormalizedRect(
                    x = round3(left.toDouble() / GRID_COLUMNS),
                    y = round3(top.toDouble() / GRID_ROWS),
                    width = round3((right - left + 1).toDouble() / GRID_COLUMNS),
                    height = round3((bottom - top + 1).toDouble() / GRID_ROWS),
                )
            },
            textTone = if (regionLuminance > TEXT_TONE_LUMINANCE_THRESHOLD) "dark" else "light",
        )
    }

    /** Mean absolute luminance gradient per grid cell, measured on a box-downscaled copy of the image. */
    private fun cellDetail(luminance: FloatArray, width: Int, height: Int): Array<DoubleArray> {
        val sampleWidth = min(width, DETAIL_SAMPLE_WIDTH)
        val sampleHeight = max(1, (height.toLong() * sampleWidth / width).toInt())
        val sums = DoubleArray(sampleWidth * sampleHeight)
        val counts = IntArray(sampleWidth * sampleHeight)
        for (y in 0 until height) {
            val sampleY = y * sampleHeight / height
            for (x in 0 until width) {
                val index = sampleY * sampleWidth + x * sampleWidth / width
                sums[index] += luminance[y * width + x]
                counts[index]++
            }
        }
        val small = DoubleArray(sums.size) { sums[it] / max(1, counts[it]) }

        val detail = Array(GRID_ROWS) { DoubleArray(GRID_COLUMNS) }
        val detailCounts = Array(GRID_ROWS) { IntArray(GRID_COLUMNS) }
        for (y in 0 until sampleHeight) {
            val row = y * GRID_ROWS / sampleHeight
            for (x in 0 until sampleWidth) {
                val column = x * GRID_COLUMNS / sampleWidth
                val here = small[y * sampleWidth + x]
                val right = if (x + 1 < sampleWidth) small[y * sampleWidth + x + 1] else here
                val below = if (y + 1 < sampleHeight) small[(y + 1) * sampleWidth + x] else here
                detail[row][column] += abs(right - here) + abs(below - here)
                detailCounts[row][column]++
            }
        }
        for (row in 0 until GRID_ROWS) for (column in 0 until GRID_COLUMNS) {
            detail[row][column] /= max(1, detailCounts[row][column])
        }
        return detail
    }

    /** The largest-area all-true rectangle as (left, top, right, bottom) inclusive cell indices. */
    internal fun largestTrueRectangle(grid: Array<BooleanArray>): List<Int>? {
        if (grid.isEmpty()) return null
        val columns = grid[0].size
        val heights = IntArray(columns)
        var best: List<Int>? = null
        var bestArea = 0
        for (row in grid.indices) {
            for (column in 0 until columns) heights[column] = if (grid[row][column]) heights[column] + 1 else 0
            for (left in 0 until columns) {
                var minHeight = Int.MAX_VALUE
                for (right in left until columns) {
                    minHeight = min(minHeight, heights[right])
                    if (minHeight == 0) break
                    val area = minHeight * (right - left + 1)
                    if (area > bestArea) {
                        bestArea = area
                        best = listOf(left, row - minHeight + 1, right, row)
                    }
                }
            }
        }
        return best
    }

    /** The box around pixels at least half opaque; the whole image when it has no alpha, `null` when it is empty. */
    private fun contentBox(argb: IntArray, width: Int, height: Int, hasAlpha: Boolean): NormalizedRect? {
        if (!hasAlpha) return NormalizedRect(0.0, 0.0, 1.0, 1.0)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if ((argb[y * width + x] ushr 24) >= OPAQUE_ALPHA) {
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
            }
        }
        if (right < 0) return null
        return NormalizedRect(
            x = round3(left.toDouble() / width),
            y = round3(top.toDouble() / height),
            width = round3((right - left + 1).toDouble() / width),
            height = round3((bottom - top + 1).toDouble() / height),
        )
    }

    /** The most common colours among opaque pixels, binned at 3 bits per channel and averaged within each bin. */
    private fun dominantColors(argb: IntArray, hasAlpha: Boolean): List<DominantColor> {
        val step = max(1, argb.size / COLOR_SAMPLE_TARGET)
        val counts = IntArray(512)
        val redSums = LongArray(512)
        val greenSums = LongArray(512)
        val blueSums = LongArray(512)
        var total = 0
        var i = 0
        while (i < argb.size) {
            val pixel = argb[i]
            i += step
            if (hasAlpha && (pixel ushr 24) < OPAQUE_ALPHA) continue
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val bin = ((red shr 5) shl 6) or ((green shr 5) shl 3) or (blue shr 5)
            counts[bin]++
            redSums[bin] += red.toLong()
            greenSums[bin] += green.toLong()
            blueSums[bin] += blue.toLong()
            total++
        }
        if (total == 0) return emptyList()
        return counts.indices.filter { counts[it] > 0 }
            .sortedByDescending { counts[it] }
            .take(DOMINANT_COLOR_COUNT)
            .map { bin ->
                val count = counts[bin]
                val hex = "#%02x%02x%02x".format(redSums[bin] / count, greenSums[bin] / count, blueSums[bin] / count)
                DominantColor(hex, round3(count.toDouble() / total))
            }
    }

    // sRGB channel value to linear light, precomputed since it runs for every pixel.
    private val linearChannel = DoubleArray(256) { channel ->
        val value = channel / 255.0
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(pixel: Int): Double =
        0.2126 * linearChannel[(pixel shr 16) and 0xFF] +
            0.7152 * linearChannel[(pixel shr 8) and 0xFF] +
            0.0722 * linearChannel[pixel and 0xFF]

    private fun round3(value: Double): Double = (value * 1000).roundToInt() / 1000.0
}
