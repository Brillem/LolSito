package com.example.lolsito.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentFormatTest {

    @Test
    fun `a share is shown as a whole percent`() {
        assertEquals("0%", formatPercent(0.0))
        assertEquals("29%", formatPercent(0.29073191636216766))
        assertEquals("67%", formatPercent(0.6666666666666666))
        assertEquals("100%", formatPercent(1.0))
    }

    @Test
    fun `a share rounds to the nearest percent`() {
        assertEquals("13%", formatPercent(0.125))
        assertEquals("5%", formatPercent(0.0549))
        assertEquals("6%", formatPercent(0.0551))
    }

    @Test
    fun `a share outside zero to one is clamped`() {
        assertEquals("0%", formatPercent(-0.2))
        assertEquals("100%", formatPercent(1.4))
        assertEquals("0%", formatPercent(Double.NaN))
    }
}
