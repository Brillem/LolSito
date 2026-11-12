package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsBracketRowsTest {

    private fun fixture(name: String): Standings =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(name)).toDomainStandings()

    private val played by lazy { buildStandingsRows(fixture("getStandings_worlds2026_knockouts.json")) }

    private fun List<StandingsRow>.assertUniqueKeys() {
        val keys = this.flatMap { row -> listOf(row.key) + ((row as? StandingsRow.BracketRow)?.rounds?.flatMap { c -> c.matches.map { it.key } } ?: emptyList()) }
        assertEquals("duplicate keys in $keys", keys.size, keys.toSet().size)
    }

    /** Rows between a stage header and the next one. */
    private fun List<StandingsRow>.ofStage(name: String): List<StandingsRow> {
        val start = indexOfFirst { it is StandingsRow.StageHeader && it.name == name }
        val end = drop(start + 1).indexOfFirst { it is StandingsRow.StageHeader }.let { if (it < 0) size else start + 1 + it }
        return subList(start + 1, end)
    }

    @Test
    fun `played worlds knockouts is a bracket while play-ins and swiss get their own views`() {
        played.assertUniqueKeys()
        assertEquals(listOf("Play-Ins", "Swiss", "Knockouts"), played.filterIsInstance<StandingsRow.StageHeader>().map { it.name })

        // Play-Ins is a double elimination and Swiss is drawn by round (see StandingsFormatsRowsTest).
        val playIns = played.ofStage("Play-Ins")
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.DoubleEliminationRow::class), playIns.map { it::class })
        val swiss = played.ofStage("Swiss")
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.SwissRow::class), swiss.map { it::class })

        val knockouts = played.ofStage("Knockouts")
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.BracketRow::class), knockouts.map { it::class })
        val bracket = knockouts.filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), bracket.rounds.map { it.name })
        assertEquals(listOf(4, 2, 1), bracket.rounds.map { it.matches.size })
    }

    @Test
    fun `played bracket keeps the api match order and every decided winner advances`() {
        val section = fixture("getStandings_worlds2026_knockouts.json").stages.single { it.name == "Knockouts" }.sections.single()
        val bracket = played.filterIsInstance<StandingsRow.BracketRow>().single()

        assertEquals(section.matches.map { it.match.id }, bracket.rounds.flatMap { c -> c.matches.map { it.sectionMatch.match.id } })
        bracket.rounds.zipWithNext().forEach { (round, next) ->
            round.matches.forEachIndexed { i, row ->
                val winner = row.result.winnerIndex?.let { row.sectionMatch.match.teams[it] } ?: return@forEachIndexed
                assertTrue(next.matches[i / 2].sectionMatch.match.teams.any { it.id == winner.id })
            }
        }
    }

    @Test
    fun `unplayed worlds knockouts is a bracket of placeholder matches`() {
        val rows = buildStandingsRows(fixture("getStandings_worlds2026.json"))

        rows.assertUniqueKeys()
        val bracket = rows.ofStage("Knockouts").filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(listOf(4, 2, 1), bracket.rounds.map { it.matches.size })
        assertTrue(rows.ofStage("Knockouts").none { it is StandingsRow.MatchRow })
    }

    @Test
    fun `ranked sections keep their tables and get no bracket`() {
        val rows = buildStandingsRows(fixture("getStandings_lck.json"))

        assertTrue(rows.none { it is StandingsRow.BracketRow })
        assertEquals(10, rows.count { it is StandingsRow.RankingRow })
    }

    @Test
    fun `a seven-match section whose winners do not advance falls back to the list`() {
        val matches = listOf(won("q1", "a", "b")) + List(6) { pending("m$it", "x$it", "y$it") }
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Knockouts", "knockouts", listOf(StandingsSection("Knockouts", emptyList(), matches))))))

        assertTrue(rows.none { it is StandingsRow.BracketRow })
        assertEquals(matches.map { it.match.id }, rows.filterIsInstance<StandingsRow.MatchRow>().map { it.sectionMatch.match.id })
    }

    @Test
    fun `bracket in a stage with other sections keeps the section header and the other lists`() {
        val semis = listOf(won("s1", "a", "b"), won("s2", "c", "d"), pending("f", "a", "c"))
        val stage = StandingsStage(
            "Playoffs", "playoffs",
            listOf(
                StandingsSection("Upper", emptyList(), semis),
                StandingsSection("Lower", emptyList(), listOf(pending("l1", "b", "d"))),
            ),
        )
        val rows = buildStandingsRows(Standings(listOf(stage)))

        rows.assertUniqueKeys()
        assertEquals(
            listOf(
                StandingsRow.StageHeader::class,
                StandingsRow.EliminationNotice::class,
                StandingsRow.SectionHeader::class,
                StandingsRow.BracketRow::class,
                StandingsRow.SectionHeader::class,
                StandingsRow.MatchRow::class,
            ),
            rows.map { it::class },
        )
    }

    @Test
    fun `with no spoilers the bracket hides scores and winners but still shows who advanced`() {
        val bracket = played.filterIsInstance<StandingsRow.BracketRow>().single()
        val completed = bracket.rounds.flatMap { it.matches }.filter { it.sectionMatch.state == MatchState.COMPLETED }
        assertTrue(completed.isNotEmpty())

        completed.forEach { row ->
            val shown = row.displayedResult(noSpoilers = false)
            assertNotNull(shown.score)
            assertNotNull(shown.winnerIndex)
            assertNotNull(row.displayedGameWins(0, noSpoilers = false))
            assertNotNull(row.displayedGameWins(1, noSpoilers = false))

            val hidden = row.displayedResult(noSpoilers = true)
            assertNull(hidden.score)
            assertNull(hidden.winnerIndex)
            assertEquals(TeamEmphasis.NEUTRAL, hidden.emphasisFor(0))
            assertEquals(TeamEmphasis.NEUTRAL, hidden.emphasisFor(1))
            assertNull(row.displayedGameWins(0, noSpoilers = true))
            assertNull(row.displayedGameWins(1, noSpoilers = true))
        }
        // The semifinals list the teams that advanced, whatever the mode.
        val semis = bracket.rounds[1].matches
        assertTrue(semis.all { row -> row.hasBothTeams && row.sectionMatch.match.teams.none { it.code == "TBD" } })
    }

    @Test
    fun `game wins are shown only for a match with a score`() {
        val row = StandingsRow.MatchRow("k", pending("m", "a", "b"))

        assertNull(row.displayedGameWins(0, noSpoilers = false))
        assertNull(row.displayedGameWins(5, noSpoilers = false))
        assertEquals("3", StandingsRow.MatchRow("k", won("m", "a", "b")).displayedGameWins(0, noSpoilers = false))
        assertEquals("1", StandingsRow.MatchRow("k", won("m", "a", "b")).displayedGameWins(1, noSpoilers = false))
    }

    private fun team(id: String, outcome: MatchOutcome? = null, gameWins: Int? = null) =
        Team(id, id.uppercase(), null, outcome, gameWins, id = id)

    private fun won(id: String, winner: String, loser: String) = SectionMatch(
        MatchState.COMPLETED,
        Match(id, listOf(team(winner, MatchOutcome.WIN, 3), team(loser, MatchOutcome.LOSS, 1)), format = null),
    )

    private fun pending(id: String, first: String, second: String) =
        SectionMatch(MatchState.UNSTARTED, Match(id, listOf(team(first), team(second)), format = null))
}
