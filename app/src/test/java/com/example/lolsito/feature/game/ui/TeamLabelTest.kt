package com.example.lolsito.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamLabelTest {

    @Test
    fun `a known code identifies the team`() {
        assertEquals("MVK", teamLabel("MVK", fallback = "Blue side"))
    }

    @Test
    fun `a missing or blank code falls back to the side`() {
        assertEquals("Blue side", teamLabel(null, fallback = "Blue side"))
        assertEquals("Red side", teamLabel("  ", fallback = "Red side"))
    }
}
