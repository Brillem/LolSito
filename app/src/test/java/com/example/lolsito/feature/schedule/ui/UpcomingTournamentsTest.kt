package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.League
import com.example.lolsito.feature.standings.domain.Tournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class UpcomingTournamentsTest {

    private val today = LocalDate.parse("2026-11-16")

    private fun tournament(slug: String, start: String?) =
        Tournament(id = "id-$slug", slug = slug, startDate = start?.let(LocalDate::parse), endDate = null)

    private fun names(list: List<UpcomingTournament>) = list.map { it.name }

    @Test
    fun `only tournaments starting after today are upcoming`() {
        val result = upcomingTournaments(
            listOf("l" to listOf(
                tournament("past", "2026-10-01"),
                tournament("today", "2026-11-16"),
                tournament("tomorrow", "2026-11-17"),
            )),
            emptyList(),
            today,
        )
        assertEquals(listOf("Tomorrow"), names(result))
    }

    @Test
    fun `a tournament without a start date is not upcoming`() {
        val result = upcomingTournaments(listOf("l" to listOf(tournament("undated", null))), emptyList(), today)
        assertEquals(emptyList<UpcomingTournament>(), result)
    }

    @Test
    fun `tournaments are sorted by start date soonest first across leagues`() {
        val result = upcomingTournaments(
            listOf(
                "a" to listOf(tournament("late", "2027-06-01")),
                "b" to listOf(tournament("early", "2027-01-01"), tournament("middle", "2027-03-01")),
            ),
            emptyList(),
            today,
        )
        assertEquals(listOf("Early", "Middle", "Late"), names(result))
    }

    @Test
    fun `ties on the start date go to the earlier league and then to the name`() {
        val result = upcomingTournaments(
            listOf(
                "a" to listOf(tournament("zeta", "2027-01-01"), tournament("alpha", "2027-01-01")),
                "b" to listOf(tournament("aardvark", "2027-01-01")),
            ),
            emptyList(),
            today,
        )
        assertEquals(listOf("Alpha", "Zeta", "Aardvark"), names(result))
    }

    @Test
    fun `at most five are returned and the soonest ones win`() {
        val tournaments = (1..8).map { tournament("t$it", "2027-0$it-01") }
        val result = upcomingTournaments(listOf("l" to tournaments.reversed()), emptyList(), today)
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5"), names(result))
    }

    @Test
    fun `a league given twice does not repeat its tournaments`() {
        val shared = tournament("worlds_2027", "2027-10-01")
        val result = upcomingTournaments(listOf("w" to listOf(shared), "w" to listOf(shared)), emptyList(), today)
        assertEquals(1, result.size)
    }

    @Test
    fun `the row carries the league, the tournament, the label, the start and the league logo`() {
        val leagues = listOf(League("w", "worlds", "Worlds", "INTERNATIONAL", "logo-url"))
        val result = upcomingTournaments(listOf("w" to listOf(tournament("worlds_2027", "2027-10-01"))), leagues, today)
        assertEquals(
            listOf(UpcomingTournament("w", "id-worlds_2027", "Worlds 2027", LocalDate.parse("2027-10-01"), "logo-url")),
            result,
        )
    }

    @Test
    fun `a league missing from the list has no logo`() {
        val result = upcomingTournaments(listOf("x" to listOf(tournament("t", "2027-01-01"))), emptyList(), today)
        assertNull(result.single().leagueImageUrl)
    }

    @Test
    fun `the start reads month and day, with the year only when it is not this year`() {
        assertEquals("starts Dec 10", startsLabel(LocalDate.parse("2026-12-10"), today, Locale.US))
        assertEquals("starts Jan 14, 2028", startsLabel(LocalDate.parse("2028-01-14"), today, Locale.US))
        assertEquals("starts Mar 10, 2027", startsLabel(LocalDate.parse("2027-03-10"), today, Locale.US))
    }

    @Test
    fun `the distance is spelled out within thirty days`() {
        assertEquals("tomorrow", startsInLabel(LocalDate.parse("2026-11-17"), today))
        assertEquals("in 2 days", startsInLabel(LocalDate.parse("2026-11-18"), today))
        assertEquals("in 12 days", startsInLabel(LocalDate.parse("2026-11-28"), today))
        assertEquals("in 30 days", startsInLabel(today.plusDays(30), today))
    }

    @Test
    fun `no distance further than thirty days or for a start that is not ahead`() {
        assertNull(startsInLabel(today.plusDays(31), today))
        assertNull(startsInLabel(today, today))
        assertNull(startsInLabel(today.minusDays(3), today))
    }
}
