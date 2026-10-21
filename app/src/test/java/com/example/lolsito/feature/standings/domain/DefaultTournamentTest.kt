package com.example.lolsito.feature.standings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DefaultTournamentTest {

    // API order: newest first.
    private val tournaments = listOf(
        tournament("summer", "2026-06-10", "2026-08-30"),
        tournament("spring", "2026-01-15", "2026-04-20"),
        tournament("cup", "2025-11-01", "2025-12-15"),
    )

    @Test
    fun `today inside a range picks that tournament`() {
        assertEquals("spring", pickDefaultTournament(tournaments, date("2026-03-01"))?.id)
    }

    @Test
    fun `range bounds are inclusive`() {
        assertEquals("summer", pickDefaultTournament(tournaments, date("2026-06-10"))?.id)
        assertEquals("summer", pickDefaultTournament(tournaments, date("2026-08-30"))?.id)
    }

    @Test
    fun `between tournaments picks the most recent one already started`() {
        assertEquals("spring", pickDefaultTournament(tournaments, date("2026-05-15"))?.id)
        assertEquals("summer", pickDefaultTournament(tournaments, date("2026-10-20"))?.id)
    }

    @Test
    fun `before every tournament picks the first one`() {
        assertEquals("summer", pickDefaultTournament(tournaments, date("2020-01-01"))?.id)
    }

    @Test
    fun `empty list picks nothing`() {
        assertNull(pickDefaultTournament(emptyList(), date("2026-03-01")))
    }

    @Test
    fun `tournaments without dates are only used as the last fallback`() {
        val undated = Tournament("undated", "undated", null, null)
        assertEquals("spring", pickDefaultTournament(listOf(undated) + tournaments, date("2026-03-01"))?.id)
        assertEquals("undated", pickDefaultTournament(listOf(undated), date("2026-03-01"))?.id)
    }

    private fun date(value: String) = LocalDate.parse(value)

    private fun tournament(id: String, start: String, end: String) =
        Tournament(id = id, slug = id, startDate = date(start), endDate = date(end))
}
