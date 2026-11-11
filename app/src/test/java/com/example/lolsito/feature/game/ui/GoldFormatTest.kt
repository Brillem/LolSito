package com.example.lolsito.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GoldFormatTest {

    @Test
    fun `gold under a thousand is shown as is`() {
        assertEquals("0", formatGold(0))
        assertEquals("999", formatGold(999))
    }

    @Test
    fun `gold from a thousand is shown in thousands with one decimal`() {
        assertEquals("1.0k", formatGold(1000))
        assertEquals("31.9k", formatGold(31850))
        assertEquals("70.7k", formatGold(70731))
    }
}
