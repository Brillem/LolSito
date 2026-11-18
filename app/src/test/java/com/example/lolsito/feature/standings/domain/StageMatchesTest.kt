package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.series
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StageMatchesTest {

    private val utc = ZoneOffset.UTC

    private fun team(name: String, code: String = name.take(3).uppercase()) = Team(name, code, "logo-$name", null, null, id = "id-$name")

    private fun ranked(name: String, vararg rankings: Ranking) =
        StandingsStage(name, name.lowercase(), listOf(StandingsSection(name, rankings.toList(), emptyList())))

    private fun bracket(name: String, vararg matches: SectionMatch) =
        StandingsStage(name, name.lowercase(), listOf(StandingsSection(name, emptyList(), matches.toList())))

    private fun sectionMatch(id: String, state: MatchState) =
        SectionMatch(state, Match(id, listOf(team("Alpha"), team("Beta")), null))

    private val notPlayed = ranked("Groups", Ranking(1, team("Alpha"), 0, 0), Ranking(1, team("Beta"), 0, 0))
    private val played = ranked("Groups", Ranking(1, team("Alpha"), 2, 0), Ranking(2, team("Beta"), 0, 2))

    // Default stage

    @Test
    fun `the default stage is the one with a match in progress`() {
        val stages = listOf(played, bracket("Playoffs", sectionMatch("m1", MatchState.IN_PROGRESS)), bracket("Finals", sectionMatch("m2", MatchState.UNSTARTED)))

        assertEquals(1, defaultStageIndex(stages))
    }

    @Test
    fun `without a live match the default is the latest stage with played matches`() {
        val stages = listOf(
            played,
            bracket("Playoffs", sectionMatch("m1", MatchState.COMPLETED), sectionMatch("m2", MatchState.UNSTARTED)),
            bracket("Finals", sectionMatch("m3", MatchState.UNSTARTED)),
        )

        assertEquals(1, defaultStageIndex(stages))
    }

    @Test
    fun `a ranked stage counts as played once a team has a record`() {
        val stages = listOf(played, bracket("Playoffs", sectionMatch("m1", MatchState.UNSTARTED)))

        assertEquals(0, defaultStageIndex(stages))
    }

    @Test
    fun `nothing played yet defaults to the first stage`() {
        val stages = listOf(notPlayed, bracket("Playoffs", sectionMatch("m1", MatchState.UNSTARTED)))

        assertEquals(0, defaultStageIndex(stages))
    }

    // Stage tabs and selector style

    @Test
    fun `tabs use the API names and keys stay unique`() {
        val tabs = stageTabs(Standings(listOf(played, played.copy(name = "Groups"), bracket("Playoffs"))))

        assertEquals(listOf("Groups", "Groups", "Playoffs"), tabs.map { it.name })
        assertEquals(3, tabs.map { it.key }.toSet().size)
    }

    @Test
    fun `up to three short names fit segments, more or longer names scroll`() {
        assertTrue(stageSelectorFitsSegments(listOf("Groups", "Play-Ins", "Knockouts")))
        assertTrue(stageSelectorFitsSegments(listOf("Regular Season", "Playoffs")))
        assertFalse(stageSelectorFitsSegments(listOf("Groups", "Play-Ins", "Knockouts", "Finals")))
        assertFalse(stageSelectorFitsSegments(listOf("Regional Championship", "Groups", "Play-Ins")))
    }

    // Regular stage matches

    private val tournament = Tournament("t", "t", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20))

    @Test
    fun `regular stage matches are those within the tournament dates, inclusive`() {
        val events = listOf(
            series("before", 4, "Alpha", "Beta"),
            series("first", 5, "Alpha", "Beta"),
            series("last", 20, "Alpha", "Beta"),
            series("after", 21, "Alpha", "Beta"),
        )

        val matches = regularStageMatches(events, tournament, Standings(listOf(played)), utc)

        assertEquals(listOf("first", "last"), matches.map { it.matchId })
    }

    @Test
    fun `matches of an elimination stage are left out, and so are events without a match`() {
        val standings = Standings(listOf(played, bracket("Playoffs", sectionMatch("final", MatchState.UNSTARTED))))
        val showMatch = series("x", 10, "Alpha", "Beta").copy(match = null)
        val events = listOf(series("week1", 6, "Alpha", "Beta"), series("final", 19, "Alpha", "Beta"), showMatch)

        val matches = regularStageMatches(events, tournament, standings, utc)

        assertEquals(listOf("week1"), matches.map { it.matchId })
        assertEquals(setOf("final"), eliminationMatchIds(standings))
    }

    // Team filter

    @Test
    fun `an event involves a team by name, ignoring case and spaces, or by code`() {
        val event = series("m", 6, "Alpha", "Beta")

        assertTrue(event.involvesTeam(team(" alpha ", code = "ZZZ")))
        assertTrue(event.involvesTeam(team("Renamed", code = "bet")))
        assertFalse(event.involvesTeam(team("Gamma")))
    }

    @Test
    fun `team options follow table order with favorites first, without placeholders or repeats`() {
        val stage = StandingsStage(
            "Groups", "groups",
            listOf(
                StandingsSection("A", listOf(Ranking(1, team("Alpha"), 2, 0), Ranking(2, team("Beta"), 1, 1)), emptyList()),
                StandingsSection(
                    "B",
                    listOf(Ranking(1, team("Gamma"), 2, 0), Ranking(2, team("Alpha"), 0, 0), Ranking(null, Team("TBD", "TBD", null, null, null, id = "0"), null, null)),
                    emptyList(),
                ),
            ),
        )

        val options = stageTeamOptions(stage, favoriteNames = listOf("gamma"))

        assertEquals(listOf("Gamma", "Alpha", "Beta"), options.map { it.team.name })
        assertEquals(listOf(true, false, false), options.map { it.favorite })
    }

    @Test
    fun `a team's ranking in the stage is found by id, else by name`() {
        assertEquals(2, stageRankingOf(played, team("Beta"))?.ordinal)
        assertEquals(1, stageRankingOf(played, Team("ALPHA", "A", null, null, null, id = "other"))?.ordinal)
        assertNull(stageRankingOf(played, team("Gamma")))
    }

    @Test
    fun `the standing line is the ordinal and the record, each part degrading on its own`() {
        assertEquals("2nd · 5-0", stageStandingLine(Ranking(2, team("A"), 5, 0)))
        assertEquals("5-0", stageStandingLine(Ranking(null, team("A"), 5, 0)))
        assertEquals("1st", stageStandingLine(Ranking(1, team("A"), null, 0)))
        assertNull(stageStandingLine(Ranking(null, team("A"), null, null)))
    }

    // Paging the schedule over the tournament dates

    private fun page(vararg events: ScheduleEvent, older: String? = null, newer: String? = null) = SchedulePage(events.toList(), older, newer)

    @Test
    fun `pages go older until they pass the start and newer until they pass the end`() = runTest {
        val repository = FakeScheduleRepository().apply {
            pages[null] = page(series("m10", 10, "A", "B"), older = "o1", newer = "n1")
            pages["o1"] = page(series("m6", 6, "A", "B"), older = "o2")
            pages["o2"] = page(series("m3", 3, "A", "B"), older = "o3")
            pages["n1"] = page(series("m22", 22, "A", "B"), newer = "n2")
        }

        val events = repository.loadEventsBetween("lck", tournament.startDate!!, tournament.endDate!!, utc)

        assertEquals(listOf(null, "o1", "o2", "n1"), repository.requests.map { it.second })
        assertEquals(listOf("lck"), repository.requests.first().first)
        assertEquals(listOf("m3", "m6", "m10", "m22"), events.map { it.matchId })
    }

    @Test
    fun `paging stops at the page cap even when the range is not covered`() = runTest {
        val repository = FakeScheduleRepository().apply {
            pages[null] = page(series("m15", 15, "A", "B"), older = "o1")
            pages["o1"] = page(series("m14", 14, "A", "B"), older = "o2")
            pages["o2"] = page(series("m13", 13, "A", "B"), older = "o3")
        }

        repository.loadEventsBetween("lck", tournament.startDate!!, tournament.endDate!!, utc, maxPages = 2)

        assertEquals(listOf(null, "o1"), repository.requests.map { it.second })
    }

    @Test
    fun `an empty page in a direction stops paging that way`() = runTest {
        val repository = FakeScheduleRepository().apply {
            pages[null] = page(series("m15", 15, "A", "B"), older = "o1")
            pages["o1"] = page(older = "o2")
        }

        repository.loadEventsBetween("lck", tournament.startDate!!, tournament.endDate!!, utc)

        assertEquals(listOf(null, "o1"), repository.requests.map { it.second })
    }
}
