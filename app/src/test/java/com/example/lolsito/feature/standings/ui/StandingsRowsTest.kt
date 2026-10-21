package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsRowsTest {

    private fun fixture(name: String): Standings =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(name)).toDomainStandings()

    private fun List<StandingsRow>.assertUniqueKeys() =
        assertEquals("duplicate keys in ${map { it.key }}", size, map { it.key }.toSet().size)

    @Test
    fun `lck rows have unique keys and a table per ranked section`() {
        val rows = buildStandingsRows(fixture("getStandings_lck.json"))

        rows.assertUniqueKeys()
        assertEquals(2, rows.count { it is StandingsRow.TableHeader })
        assertEquals(10, rows.count { it is StandingsRow.RankingRow })
        val firstRanking = rows.filterIsInstance<StandingsRow.RankingRow>().first()
        assertEquals("GEN", firstRanking.ranking.team.code)
        assertEquals("1", firstRanking.position)
        assertEquals("5-3", firstRanking.record)
        val stages = rows.filterIsInstance<StandingsRow.StageHeader>()
        assertEquals(listOf(false, true, true), stages.map { it.isElimination })
    }

    @Test
    fun `worlds 2026 rows fall back to elimination notices with match lists`() {
        val rows = buildStandingsRows(fixture("getStandings_worlds2026.json"))

        rows.assertUniqueKeys()
        assertTrue(rows.none { it is StandingsRow.TableHeader || it is StandingsRow.RankingRow })
        val notices = rows.filterIsInstance<StandingsRow.EliminationNotice>()
        assertEquals(3, notices.size)
        assertTrue(notices.all { it.hasMatches })
        val matches = rows.filterIsInstance<StandingsRow.MatchRow>()
        assertEquals(46, matches.size)
        assertEquals("3 - 0", matches.first().result.score)
    }

    @Test
    fun `elimination stage without matches shows a notice that has no matches`() {
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Knockouts", "knockouts", emptyList()))))

        val notice = rows.filterIsInstance<StandingsRow.EliminationNotice>().single()
        assertFalse(notice.hasMatches)
        assertTrue(rows.none { it is StandingsRow.MatchRow })
    }

    @Test
    fun `ranked stage with an empty section shows an empty section row`() {
        val stage = StandingsStage(
            "Groups", "groups",
            listOf(section("A", rankings = listOf(ranking(1, "x"))), section("B")),
        )
        val rows = buildStandingsRows(Standings(listOf(stage)))

        assertEquals(1, rows.count { it is StandingsRow.EmptySection })
        assertEquals(listOf("A", "B"), rows.filterIsInstance<StandingsRow.SectionHeader>().map { it.name })
    }

    @Test
    fun `section header is omitted when a single section repeats the stage name`() {
        val stage = StandingsStage("Swiss", "swiss", listOf(section("Swiss", matches = listOf(match("m1")))))
        val rows = buildStandingsRows(Standings(listOf(stage)))

        assertTrue(rows.none { it is StandingsRow.SectionHeader })
    }

    @Test
    fun `repeated stages, teams and matches still yield unique keys`() {
        val section = section(
            "A",
            rankings = listOf(ranking(1, "x"), ranking(1, "x"), ranking(null, "x")),
            matches = listOf(match("m1"), match("m1")),
        )
        val elimination = section("A", matches = listOf(match("m1"), match("m1")))
        val stages = listOf(
            StandingsStage("Groups", "groups", listOf(section, section)),
            StandingsStage("Groups", "groups", listOf(section, section)),
            StandingsStage("Bracket", null, listOf(elimination, elimination)),
            StandingsStage("Bracket", null, listOf(elimination)),
        )

        val rows = buildStandingsRows(Standings(stages))

        rows.assertUniqueKeys()
        assertTrue(rows.filterIsInstance<StandingsRow.RankingRow>().any { it.position == "-" })
    }

    @Test
    fun `ranking row without a record shows a dash instead of 0-0`() {
        val unknown = Ranking(1, Team("a", "AAA", null, null, null, id = "a"), wins = null, losses = null)
        assertEquals("-", StandingsRow.RankingRow("k", unknown).record)
    }

    private fun section(name: String, rankings: List<Ranking> = emptyList(), matches: List<SectionMatch> = emptyList()) =
        StandingsSection(name, rankings, matches)

    private fun ranking(ordinal: Int?, id: String) =
        Ranking(ordinal, Team(id, id.uppercase(), null, null, null, id = id), wins = 1, losses = 2)

    private fun match(id: String) = SectionMatch(MatchState.UNSTARTED, Match(id, emptyList(), bestOf = null))
}
