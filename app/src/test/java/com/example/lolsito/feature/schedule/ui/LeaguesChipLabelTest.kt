package com.example.lolsito.feature.schedule.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaguesChipLabelTest {

    @Test
    fun `no league selected reads Leagues`() {
        assertEquals("Leagues", leaguesChipLabel(emptyList()))
    }

    @Test
    fun `one league shows its name`() {
        assertEquals("LCK", leaguesChipLabel(listOf("LCK")))
    }

    @Test
    fun `several leagues show only the count`() {
        assertEquals("3", leaguesChipLabel(listOf("LCK", "LEC", "LCS")))
    }
}
