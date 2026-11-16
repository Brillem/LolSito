package com.example.lolsito.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** Pixels of an image made of each color repeated as many times as its count. */
    private fun image(vararg parts: Pair<Int, Int>): IntArray =
        parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    private val red = argb(226, 1, 45)
    private val cyan = argb(40, 200, 230)
    private val pink = argb(240, 90, 160)
    private val blue = argb(30, 80, 220)
    private val white = argb(255, 255, 255)
    private val gray = argb(128, 128, 128)
    private val black = argb(0, 0, 0)

    @Test
    fun `a logo of one color gives that color`() {
        assertEquals(red, accentOf(image(red to 100)))
    }

    @Test
    fun `the color covering most of the logo wins`() {
        assertEquals(cyan, accentOf(image(cyan to 70, pink to 30)))
        assertEquals(pink, accentOf(image(pink to 70, cyan to 30)))
    }

    @Test
    fun `white, gray and black are not colors, so the colored part gives the accent`() {
        assertEquals(red, accentOf(image(white to 60, gray to 15, black to 15, red to 10)))
    }

    @Test
    fun `a logo of white, gray and black has no accent`() {
        assertNull(accentOf(image(white to 50, gray to 30, black to 20)))
    }

    @Test
    fun `transparent pixels are not part of the logo`() {
        assertNull(accentOf(image(argb(226, 1, 45, a = 0) to 100)))
        assertEquals(blue, accentOf(image(argb(226, 1, 45, a = 0) to 100, blue to 10)))
    }

    @Test
    fun `an empty image has no accent`() {
        assertNull(accentOf(IntArray(0)))
    }

    @Test
    fun `a few stray colored pixels in a white logo are not an accent`() {
        assertNull(accentOf(image(white to 1000, red to 5)))
    }

    @Test
    fun `shades of one color count together`() {
        val darkerRed = argb(176, 1, 35)
        val accent = accentOf(image(red to 40, darkerRed to 35, blue to 60))!!

        assertTrue("red should win over blue", (accent shr 16 and 0xFF) > (accent and 0xFF))
    }

    @Test
    fun `a color too dark to see on black is lightened, keeping its hue`() {
        val navy = argb(16, 16, 96)

        val accent = accentOf(image(navy to 100))!!

        val r = accent shr 16 and 0xFF
        val g = accent shr 8 and 0xFF
        val b = accent and 0xFF
        assertTrue("blue stays the strongest channel", b > r && b > g)
        assertTrue("bright enough: $b", b >= 178)
        assertEquals(r, g)
        assertEquals(255, accent ushr 24)
    }
}
