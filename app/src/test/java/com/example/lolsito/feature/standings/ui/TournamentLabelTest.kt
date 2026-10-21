package com.example.lolsito.feature.standings.ui

import com.example.lolsito.feature.standings.domain.Tournament
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TournamentLabelTest {
    @Test
    fun `slug words are capitalized`() {
        assertEquals("Worlds 2026", tournament("worlds_2026").label)
        assertEquals("Lck Split 2 2026", tournament("lck-split_2_2026").label)
    }

    @Test
    fun `blank slug falls back to the id`() {
        assertEquals("123", tournament(" ").label)
    }

    @Test
    fun `date range is shown when both dates are known`() {
        assertEquals("2026-10-15 – 2026-11-15", tournament("x", "2026-10-15", "2026-11-15").dateRange)
        assertEquals(null, tournament("x").dateRange)
    }

    private fun tournament(slug: String, start: String? = null, end: String? = null) =
        Tournament("123", slug, start?.let(LocalDate::parse), end?.let(LocalDate::parse))
}
