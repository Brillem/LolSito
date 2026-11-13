package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.testutil.series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MatchHistoryTest {

    @Test
    fun `recent form is the last five finished series, newest first, without the current match`() {
        val events = (1..7).map { day -> series("m$day", day, "Alpha", "Beta", firstWon = true) } +
            series("current", 8, "Alpha", "Beta", firstWon = true) +
            series("upcoming", 9, "Alpha", "Beta", state = MatchState.UNSTARTED) +
            series("live", 10, "Alpha", "Beta", state = MatchState.IN_PROGRESS) +
            series("other", 11, "Gamma", "Delta", firstWon = true)

        val form = recentForm(events.shuffled(), "Alpha", currentMatchId = "current")

        assertEquals(listOf("m7", "m6", "m5", "m4", "m3"), form.map { it.matchId })
    }

    @Test
    fun `a series is a win or a loss by its winner, with the opponent's code`() {
        val events = listOf(
            series("won", 1, "Alpha", "Beta", firstWon = true),
            series("lost", 2, "Gamma", "Alpha", firstWon = true), // Gamma won
        )

        assertEquals(
            listOf(
                FormEntry("lost", FormOutcome.LOSS, "GAM"),
                FormEntry("won", FormOutcome.WIN, "BET"),
            ),
            recentForm(events, "Alpha", currentMatchId = "none"),
        )
    }

    @Test
    fun `a finished series with no known winner is left out of the form`() {
        val events = listOf(
            series("won", 1, "Alpha", "Beta", firstWon = true),
            series("unknown", 2, "Alpha", "Beta", firstWon = null),
        )

        assertEquals(listOf("won"), recentForm(events, "Alpha", currentMatchId = "none").map { it.matchId })
    }

    @Test
    fun `head to head keeps only the finished meetings of both teams, at most five, newest first, without the current match`() {
        val events = (1..7).map { day -> series("m$day", day, "Alpha", "Beta", firstWon = true) } +
            series("current", 8, "Alpha", "Beta", firstWon = true) +
            series("upcoming", 9, "Alpha", "Beta", state = MatchState.UNSTARTED) +
            series("alpha-only", 10, "Alpha", "Gamma", firstWon = true) +
            series("beta-only", 11, "Gamma", "Beta", firstWon = true)

        val meetings = headToHead(events.shuffled(), "Alpha", "Beta", currentMatchId = "current", zoneId = ZoneOffset.UTC)

        assertEquals(listOf("m7", "m6", "m5", "m4", "m3"), meetings.map { it.matchId })
    }

    @Test
    fun `a meeting has its date, league and score in the order of the current match's teams`() {
        // That series listed Beta first; the current match lists Alpha first.
        val events = listOf(series("m1", 3, "Beta", "Alpha", firstWon = true, firstWins = 2, secondWins = 1))

        val meeting = headToHead(events, "Alpha", "Beta", currentMatchId = "none", zoneId = ZoneOffset.UTC).single()

        assertEquals(Meeting("m1", LocalDate.of(2026, 1, 3), "League", score = "1 - 2", winnerIndex = 1), meeting)
    }

    @Test
    fun `teams are recognized by name ignoring case and surrounding spaces`() {
        val events = listOf(series("m1", 1, " ALPHA ", "beta", firstWon = true))

        assertEquals(listOf(FormEntry("m1", FormOutcome.WIN, "BET")), recentForm(events, "alpha", "none"))
        assertEquals(listOf(FormEntry("m1", FormOutcome.LOSS, "ALP")), recentForm(events, "  Beta", "none"))
        assertEquals(listOf("m1"), headToHead(events, "Alpha  ", " BETA", "none", ZoneOffset.UTC).map { it.matchId })
    }

    @Test
    fun `older history is needed while a team has fewer than five finished series`() {
        val alphaFive = (1..5).map { day -> series("a$day", day, "Alpha", "Gamma", firstWon = true) }
        val betaFour = (1..4).map { day -> series("b$day", day, "Beta", "Gamma", firstWon = true) }

        assertTrue(needsOlderHistory(alphaFive + betaFour, listOf("Alpha", "Beta"), "none"))
        assertFalse(
            needsOlderHistory(
                alphaFive + betaFour + series("b5", 5, "Beta", "Gamma", firstWon = true),
                listOf("Alpha", "Beta"),
                "none",
            ),
        )
    }

    @Test
    fun `the schedule filter is both home leagues plus the international ones, sorted and without repeats`() {
        assertEquals(
            (InternationalLeagues.ids + listOf("1", "2")).sorted(),
            matchHistoryLeagueIds(listOf("2", "1")),
        )
        assertEquals((InternationalLeagues.ids + "1").sorted(), matchHistoryLeagueIds(listOf("1", "1")))
        assertEquals(InternationalLeagues.ids.sorted(), matchHistoryLeagueIds(listOf(null, InternationalLeagues.MSI)))
    }

    @Test
    fun `the history has one form row per team of the current match, in its order`() {
        val alpha = Team("Alpha", "ALP", null, null, null)
        val beta = Team("Beta", "BET", null, null, null)
        val events = listOf(series("m1", 1, "Beta", "Alpha", firstWon = true))

        val history = buildMatchHistory(events, listOf(alpha, beta), "none", ZoneOffset.UTC)

        assertEquals(
            listOf(
                TeamForm(alpha, listOf(FormEntry("m1", FormOutcome.LOSS, "BET"))),
                TeamForm(beta, listOf(FormEntry("m1", FormOutcome.WIN, "ALP"))),
            ),
            history.form,
        )
        assertEquals(listOf("m1"), history.meetings.map { it.matchId })
    }

    @Test
    fun `series played after the current match are neither form nor previous meetings`() {
        val alpha = Team("Alpha", "ALP", null, null, null)
        val beta = Team("Beta", "BET", null, null, null)
        val events = listOf(
            series("before", 1, "Alpha", "Beta", firstWon = true),
            series("current", 2, "Alpha", "Beta", firstWon = true),
            series("after", 3, "Alpha", "Beta", firstWon = true),
            series("after-other", 4, "Alpha", "Gamma", firstWon = true),
        )

        val history = buildMatchHistory(events, listOf(alpha, beta), "current", ZoneOffset.UTC)

        assertEquals(listOf(listOf("before"), listOf("before")), history.form.map { row -> row.entries.map { it.matchId } })
        assertEquals(listOf("before"), history.meetings.map { it.matchId })
    }

    @Test
    fun `when the current match is not among the events every finished series counts`() {
        val events = listOf(
            series("m1", 1, "Alpha", "Beta", firstWon = true),
            series("m2", 2, "Alpha", "Beta", firstWon = true),
        )

        assertEquals(listOf("m2", "m1"), recentForm(events, "Alpha", currentMatchId = "elsewhere").map { it.matchId })
    }
}
