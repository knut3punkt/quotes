package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageAnalysisTest {

    @Test
    fun `background calm region is the smooth half, and light grey asks for dark text`() {
        val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        val random = Random(1)
        for (y in 0 until 180) for (x in 0 until 320) {
            val grey = if (x < 160) 200 else if (random.nextBoolean()) 0 else 255
            image.setRGB(x, y, (grey shl 16) or (grey shl 8) or grey)
        }

        val result = ImageAnalysis.analyze(pngBytes(image), ImageKind.BACKGROUND)
        val layout = Json.decodeFromJsonElement(BackgroundLayout.serializer(), result.layout)

        assertEquals(320, result.width)
        assertEquals(180, result.height)
        assertFalse(result.hasAlpha)
        assertEquals(0.0, result.transparentFraction)
        assertEquals(ImageAnalysis.GRID_ROWS, layout.cells.size)
        assertEquals(ImageAnalysis.GRID_COLUMNS, layout.cells.first().size)
        assertEquals(NormalizedRect(0.0, 0.0, 0.5, 1.0), layout.calmRegion)
        assertEquals("dark", layout.textTone)
        assertTrue(layout.cells[4][2].detail < layout.cells[4][12].detail)
    }

    @Test
    fun `a uniformly smooth dark background is calm everywhere and asks for light text`() {
        val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 90) for (x in 0 until 160) image.setRGB(x, y, 0x101830)

        val result = ImageAnalysis.analyze(pngBytes(image), ImageKind.BACKGROUND)
        val layout = Json.decodeFromJsonElement(BackgroundLayout.serializer(), result.layout)

        assertEquals(NormalizedRect(0.0, 0.0, 1.0, 1.0), layout.calmRegion)
        assertEquals("light", layout.textTone)
        assertEquals("#101830", result.dominantColors.single().hex)
        assertEquals(1.0, result.dominantColors.single().weight)
    }

    @Test
    fun `element content box and transparency come from the alpha channel`() {
        val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        for (y in 30 until 80) for (x in 20 until 60) image.setRGB(x, y, 0xFFE02010.toInt())

        val result = ImageAnalysis.analyze(pngBytes(image), ImageKind.ELEMENT)
        val layout = Json.decodeFromJsonElement(ElementLayout.serializer(), result.layout)

        assertTrue(result.hasAlpha)
        assertEquals(0.8, result.transparentFraction)
        assertEquals(NormalizedRect(0.2, 0.3, 0.4, 0.5), layout.contentBox)
        // Only opaque pixels count towards colour.
        assertEquals("#e02010", result.dominantColors.single().hex)
    }

    @Test
    fun `a fully transparent element has no content box`() {
        val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)

        val result = ImageAnalysis.analyze(pngBytes(image), ImageKind.ELEMENT)

        assertNull(Json.decodeFromJsonElement(ElementLayout.serializer(), result.layout).contentBox)
        assertEquals(1.0, result.transparentFraction)
        assertTrue(result.dominantColors.isEmpty())
    }

    @Test
    fun `largest true rectangle prefers area over shape`() {
        val grid = arrayOf(
            booleanArrayOf(true, true, false, false),
            booleanArrayOf(true, true, true, true),
            booleanArrayOf(true, true, true, true),
        )
        assertEquals(listOf(0, 1, 3, 2), ImageAnalysis.largestTrueRectangle(grid))
        assertNull(ImageAnalysis.largestTrueRectangle(arrayOf(booleanArrayOf(false, false))))
    }

    private fun pngBytes(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
