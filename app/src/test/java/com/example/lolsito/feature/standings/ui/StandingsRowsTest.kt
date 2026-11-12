package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
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
        assertEquals(3, rows.count { it is StandingsRow.EliminationNotice })
        assertTrue(rows.none { it is StandingsRow.EmptySection })
        val matches = rows.filterIsInstance<StandingsRow.MatchRow>()
        // Play-Ins (6) and Swiss (33) stay lists; the 7 Knockouts matches form a bracket.
        assertEquals(39, matches.size)
        assertEquals(1, rows.count { it is StandingsRow.BracketRow })
        assertEquals("3 - 0", matches.first().result.score)
    }

    @Test
    fun `stage without sections shows an empty row instead of an elimination notice`() {
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Knockouts", "knockouts", emptyList()))))

        assertEquals(1, rows.count { it is StandingsRow.EmptySection })
        assertTrue(rows.none { it is StandingsRow.EliminationNotice || it is StandingsRow.MatchRow })
        assertFalse(rows.filterIsInstance<StandingsRow.StageHeader>().single().isElimination)
    }

    @Test
    fun `mixed stage shows the ranked table and the bracket matches`() {
        val stage = StandingsStage(
            "Groups", "groups",
            listOf(
                section("Group A", rankings = listOf(ranking(1, "x"), ranking(2, "y"))),
                section("Bracket", matches = listOf(match("m1"), match("m2"))),
            ),
        )
        val rows = buildStandingsRows(Standings(listOf(stage)))

        rows.assertUniqueKeys()
        assertEquals(
            listOf(
                StandingsRow.StageHeader::class,
                StandingsRow.SectionHeader::class,
                StandingsRow.TableHeader::class,
                StandingsRow.RankingRow::class,
                StandingsRow.RankingRow::class,
                StandingsRow.SectionHeader::class,
                StandingsRow.MatchRow::class,
                StandingsRow.MatchRow::class,
            ),
            rows.map { it::class },
        )
        assertEquals(listOf("m1", "m2"), rows.filterIsInstance<StandingsRow.MatchRow>().map { it.sectionMatch.match.id })
        assertFalse(rows.filterIsInstance<StandingsRow.StageHeader>().single().isElimination)
    }

    @Test
    fun `all-bracket stage shows one elimination notice and every section's matches`() {
        val stage = StandingsStage(
            "Knockouts", "knockouts",
            listOf(section("Quarterfinals", matches = listOf(match("q1"), match("q2"))), section("Final", matches = listOf(match("f1")))),
        )
        val rows = buildStandingsRows(Standings(listOf(stage)))

        rows.assertUniqueKeys()
        assertEquals(1, rows.count { it is StandingsRow.EliminationNotice })
        assertEquals(listOf("q1", "q2", "f1"), rows.filterIsInstance<StandingsRow.MatchRow>().map { it.sectionMatch.match.id })
        assertEquals(listOf("Quarterfinals", "Final"), rows.filterIsInstance<StandingsRow.SectionHeader>().map { it.name })
        assertTrue(rows.none { it is StandingsRow.EmptySection })
        assertTrue(rows.filterIsInstance<StandingsRow.StageHeader>().single().isElimination)
    }

    @Test
    fun `bracket stage keeps its notice and skips sections that are not scheduled yet`() {
        val stage = StandingsStage(
            "Knockouts", "knockouts",
            listOf(section("Quarterfinals", matches = listOf(match("q1"))), section("Final")),
        )
        val rows = buildStandingsRows(Standings(listOf(stage)))

        rows.assertUniqueKeys()
        assertEquals(1, rows.count { it is StandingsRow.EliminationNotice })
        assertEquals(listOf("q1"), rows.filterIsInstance<StandingsRow.MatchRow>().map { it.sectionMatch.match.id })
        assertTrue(rows.none { it is StandingsRow.EmptySection })
        assertEquals(listOf("Quarterfinals"), rows.filterIsInstance<StandingsRow.SectionHeader>().map { it.name })
        assertTrue(rows.filterIsInstance<StandingsRow.StageHeader>().single().isElimination)
    }

    @Test
    fun `unstarted stage without rankings or matches shows empty sections, not an elimination notice`() {
        val stage = StandingsStage("Groups", "groups", listOf(section("Group A"), section("Group B")))
        val rows = buildStandingsRows(Standings(listOf(stage)))

        rows.assertUniqueKeys()
        assertEquals(2, rows.count { it is StandingsRow.EmptySection })
        assertTrue(rows.none { it is StandingsRow.EliminationNotice })
        assertFalse(rows.filterIsInstance<StandingsRow.StageHeader>().single().isElimination)
    }

    @Test
    fun `match row is navigable only when both teams are known`() {
        val a = Team("a", "AAA", null, null, null, id = "a")
        val b = Team("b", "BBB", null, null, null, id = "b")
        fun row(vararg teams: Team) =
            StandingsRow.MatchRow("k", SectionMatch(MatchState.UNSTARTED, Match("m", teams.toList(), format = SeriesFormat(SeriesType.BEST_OF, 3))))

        assertTrue(row(a, b).isNavigable)
        assertFalse(row(a).isNavigable)
        assertFalse(row().isNavigable)
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

    private fun match(id: String) = SectionMatch(MatchState.UNSTARTED, Match(id, emptyList(), format = null))
}

class MatchRowResultTest {

    private fun row(state: MatchState, outcomes: Boolean = true) = StandingsRow.MatchRow(
        key = "match:m",
        sectionMatch = SectionMatch(
            state,
            Match(
                id = "m",
                teams = listOf(
                    Team("T1", "T1", null, com.example.lolsito.core.model.MatchOutcome.WIN.takeIf { outcomes }, 2),
                    Team("Gen.G", "GEN", null, com.example.lolsito.core.model.MatchOutcome.LOSS.takeIf { outcomes }, 1),
                ),
                format = SeriesFormat(SeriesType.BEST_OF, 3),
            ),
        ),
    )

    @Test
    fun `with the mode off the result is the formatted one`() {
        val row = row(MatchState.COMPLETED)

        assertEquals(row.result, row.displayedResult(noSpoilers = false))
        assertEquals("2 - 1", row.displayedResult(noSpoilers = false).score)
    }

    @Test
    fun `with the mode on a finished match has no score and no loser`() {
        val result = row(MatchState.COMPLETED).displayedResult(noSpoilers = true)

        assertEquals(null, result.score)
        assertEquals(null, result.winnerIndex)
        assertEquals(com.example.lolsito.core.model.TeamEmphasis.NEUTRAL, result.emphasisFor(0))
        assertEquals(com.example.lolsito.core.model.TeamEmphasis.NEUTRAL, result.emphasisFor(1))
        assertEquals("Bo3", result.formatLabel)
    }

    @Test
    fun `an unfinished match is the same with the mode on or off`() {
        val row = row(MatchState.UNSTARTED, outcomes = false)

        assertEquals(row.displayedResult(noSpoilers = false), row.displayedResult(noSpoilers = true))
    }
}
