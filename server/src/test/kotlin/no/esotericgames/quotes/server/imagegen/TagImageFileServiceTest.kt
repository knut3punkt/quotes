package no.esotericgames.quotes.server.imagegen

import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagImageFileServiceTest {

    @Test
    fun `requested width snaps up to the next derivative width`() {
        assertEquals(640, snapDerivativeWidth(100, originalWidth = 2752))
        assertEquals(1280, snapDerivativeWidth(641, originalWidth = 2752))
        assertEquals(1920, snapDerivativeWidth(1920, originalWidth = 2752))
        assertEquals(1920, snapDerivativeWidth(4000, originalWidth = 2752))
    }

    @Test
    fun `no width, or a derivative no smaller than the original, serves the original`() {
        assertNull(snapDerivativeWidth(null, originalWidth = 2752))
        assertNull(snapDerivativeWidth(0, originalWidth = 2752))
        assertNull(snapDerivativeWidth(1000, originalWidth = 1024))
        assertNull(snapDerivativeWidth(640, originalWidth = 640))
    }

    @Test
    fun `a path that escapes the storage directory is refused`() {
        val root = Path.of("storage").toAbsolutePath().normalize()

        assertEquals(root.resolve("background").resolve("1-a.png"), resolveInside(root, "background/1-a.png"))
        assertFailsWith<IllegalArgumentException> { resolveInside(root, "../secret.png") }
        assertFailsWith<IllegalArgumentException> { resolveInside(root, "background/../../secret.png") }
        assertFailsWith<IllegalArgumentException> { resolveInside(root, root.parent.resolve("x.png").toString()) }
        assertFailsWith<IllegalArgumentException> { resolveInside(root, ".") }
    }

    @Test
    fun `resize keeps the aspect ratio and alpha only when asked`() {
        val source = BufferedImage(2752, 1536, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, 0x00000000)

        val opaque = resizeToWidth(source, 1920, keepAlpha = false)
        val transparent = resizeToWidth(BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB), 640, keepAlpha = true)

        assertEquals(1920, opaque.width)
        assertEquals(1072, opaque.height)
        assertTrue(!opaque.colorModel.hasAlpha())
        assertEquals(640, transparent.width)
        assertEquals(640, transparent.height)
        assertEquals(0, transparent.getRGB(320, 320) ushr 24, "fully transparent source stays transparent")
    }
}
