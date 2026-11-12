package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TeamMatchesTest {

    private fun event(id: String?, start: String, state: MatchState, vararg teams: String) = ScheduleEvent(
        startTime = Instant.parse(start),
        state = state,
        blockName = null,
        leagueName = "LCK",
        leagueSlug = "lck",
        match = id?.let { Match(it, teams.map { name -> Team(name, name.take(3), null, null, null) }, null) },
    )

    @Test
    fun `the schedule filter is the home league plus Worlds, MSI and First Stand, sorted and without repeats`() {
        assertEquals(
            listOf(InternationalLeagues.WORLDS, InternationalLeagues.MSI, InternationalLeagues.FIRST_STAND, "98767991310872058").sorted(),
            teamScheduleLeagueIds("98767991310872058"),
        )
        assertEquals(InternationalLeagues.ids.sorted(), teamScheduleLeagueIds(InternationalLeagues.MSI))
        assertEquals(InternationalLeagues.ids.sorted(), teamScheduleLeagueIds(null))
        assertEquals(setOf("98767975604431411", "98767991325878492", "113464388705111224"), InternationalLeagues.ids.toSet())
    }

    @Test
    fun `the home league is found by name, ignoring case and spaces`() {
        val leagues = listOf(League("1", "lck", "LCK", "KOREA", null), League("2", "lec", "LEC", "EMEA", null))

        assertEquals("1", homeLeagueId(leagues, " lck"))
        assertNull(homeLeagueId(leagues, "LPL"))
        assertNull(homeLeagueId(leagues, null))
    }

    @Test
    fun `keeps only the team's matches, by name, splitting unstarted and live from the rest`() {
        val events = listOf(
            event("p1", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G"),
            event("p2", "2026-10-05T10:00:00Z", MatchState.COMPLETED, "KT Rolster", "t1 "),
            event("other", "2026-10-06T10:00:00Z", MatchState.COMPLETED, "Gen.G", "KT Rolster"),
            event("prefix", "2026-10-07T10:00:00Z", MatchState.COMPLETED, "T1 Academy", "KT Rolster"),
            event(null, "2026-10-08T10:00:00Z", MatchState.COMPLETED),
            event("u2", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "T1", "Gen.G"),
            event("live", "2026-11-11T10:00:00Z", MatchState.IN_PROGRESS, "T1", "BNK FEARX"),
            event("u1", "2026-11-12T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1"),
            event("unknown", "2026-10-09T10:00:00Z", MatchState.UNKNOWN, "T1", "DRX"),
        )

        val matches = buildTeamMatches(events, "T1", ZoneOffset.UTC)

        assertEquals(listOf("live", "u1", "u2"), matches.upcoming.flatMap { day -> day.events.map { it.matchId } })
        assertEquals(listOf("unknown", "p2", "p1"), matches.previous.flatMap { day -> day.events.map { it.matchId } })
        assertEquals(
            listOf(LocalDate.parse("2026-11-11"), LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-20")),
            matches.upcoming.map { it.date },
        )
        // Sections share dates only by accident, but their keys must never collide in one list.
        val keys = (matches.upcoming + matches.previous).flatMap { it.keys + it.headerKey }
        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun `no matching events gives two empty sections`() {
        val matches = buildTeamMatches(
            listOf(event("other", "2026-10-06T10:00:00Z", MatchState.COMPLETED, "Gen.G", "KT Rolster")),
            "T1",
            ZoneOffset.UTC,
        )

        assertEquals(emptyList<Any>(), matches.upcoming)
        assertEquals(emptyList<Any>(), matches.previous)
    }
}
