package com.example.lolsito.feature.standings.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormatTest {

    @Test
    fun `complete record shows wins and losses`() {
        assertEquals("5-3", formatRecord(wins = 5, losses = 3))
    }

    @Test
    fun `winless record is a real record, not a placeholder`() {
        assertEquals("0-0", formatRecord(wins = 0, losses = 0))
    }

    @Test
    fun `missing record shows a dash`() {
        assertEquals("-", formatRecord(wins = null, losses = null))
    }

    @Test
    fun `missing losses show a dash in their place`() {
        assertEquals("3 - -", formatRecord(wins = 3, losses = null))
    }

    @Test
    fun `missing wins show a dash in their place`() {
        assertEquals("- - 2", formatRecord(wins = null, losses = 2))
    }
}
