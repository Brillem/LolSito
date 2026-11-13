package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.feature.standings.domain.BracketCell
import com.example.lolsito.feature.standings.domain.BracketLayoutColumn
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.WinnerLink
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsBracketRowsTest {

    private fun fixture(name: String): Standings =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(name)).toDomainStandings()

    private val worlds by lazy { buildStandingsRows(fixture("getStandingsV3_worlds2026.json")) }
    private val msi by lazy { buildStandingsRows(fixture("getStandingsV3_msi2026.json")) }

    /** Every key in the list, including the cards drawn inside bracket and Swiss rows. */
    private fun List<StandingsRow>.assertUniqueKeys() {
        val keys = flatMap { row ->
            listOf(row.key) + when (row) {
                is StandingsRow.BracketRow -> row.cards.map { it.key }
                is StandingsRow.SwissRow -> row.rounds.flatMap { c -> c.matches.map { it.key } }
                else -> emptyList()
            }
        }
        assertEquals("duplicate keys in $keys", keys.size, keys.toSet().size)
    }

    /** Rows between a stage header and the next one. */
    private fun List<StandingsRow>.ofStage(name: String): List<StandingsRow> {
        val start = indexOfFirst { it is StandingsRow.StageHeader && it.name == name }
        val end = drop(start + 1).indexOfFirst { it is StandingsRow.StageHeader }.let { if (it < 0) size else start + 1 + it }
        return subList(start + 1, end)
    }

    private val StandingsRow.BracketRow.cards get() = columns.flatMap { c -> c.cells.flatMap { it.matches } }

    private fun StandingsRow.BracketRow.cellNames() = columns.map { c -> c.cells.map { it.name } }

    private fun StandingsRow.BracketRow.card(id: String) = cards.single { it.sectionMatch.match.id == id }

    /** Lines as match ids, so they read like the API links. */
    private fun StandingsRow.BracketRow.lineIds(): List<Pair<String, String>> {
        val ids = cards.associate { it.key to it.sectionMatch.match.id }
        return lines.map { ids.getValue(it.fromKey) to ids.getValue(it.toKey) }
    }

    @Test
    fun `worlds draws its play-ins and knockouts as brackets and its swiss by round`() {
        worlds.assertUniqueKeys()
        assertEquals(listOf("Play-Ins", "Swiss", "Knockouts"), worlds.filterIsInstance<StandingsRow.StageHeader>().map { it.name })
        assertTrue(worlds.none { it is StandingsRow.MatchRow })

        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.BracketRow::class), worlds.ofStage("Play-Ins").map { it::class })
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.SwissRow::class), worlds.ofStage("Swiss").map { it::class })
        val knockouts = worlds.ofStage("Knockouts")
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.BracketRow::class), knockouts.map { it::class })
        val bracket = knockouts.filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(listOf(listOf("Quarterfinals"), listOf("Semifinals"), listOf("Finals")), bracket.cellNames())
        assertEquals(listOf(4, 2, 1), bracket.columns.map { c -> c.cells.single().matches.size })
    }

    @Test
    fun `bracket cards keep the api layout order`() {
        val section = fixture("getStandingsV3_msi2026.json").stages.single { it.name == "Knockouts" }.sections.single()
        val bracket = msi.ofStage("Knockouts").filterIsInstance<StandingsRow.BracketRow>().single()

        assertEquals(section.matches.map { it.match.id }, bracket.cards.map { it.sectionMatch.match.id })
        assertEquals(
            listOf(
                listOf("Upper Bracket - Round 1"),
                listOf("Upper Bracket - Round 2", "Lower Bracket - Round 1"),
                listOf("Upper Bracket - Round 3", "Lower Bracket - Round 2"),
                listOf("Lower Bracket - Round 3"),
                listOf("Lower Bracket - Round 4"),
                listOf("Finals"),
            ),
            bracket.cellNames(),
        )
    }

    @Test
    fun `lines join each match to the match its winner plays next, never to a lower-bracket drop`() {
        val bracket = msi.ofStage("Knockouts").filterIsInstance<StandingsRow.BracketRow>().single()

        assertEquals(13, bracket.lines.size)
        bracket.lineIds().forEach { (from, to) ->
            val winner = bracket.card(from).let { row -> row.result.winnerIndex?.let { row.sectionMatch.match.teams[it] } }
            assertNotNull(winner)
            assertTrue(bracket.card(to).sectionMatch.match.teams.any { it.id == winner!!.id })
        }
        // Upper round 1 sends its first loser to lower round 1: no line for that drop.
        assertFalse(("115570934355614527" to "115570934355614563") in bracket.lineIds())
        // The upper round 3 winner goes straight to the final, three columns later.
        assertTrue(("115570934355614575" to "115570934355614605") in bracket.lineIds())
    }

    @Test
    fun `lck and lec formats that used to be lists are drawn as brackets`() {
        val lck = buildStandingsRows(fixture("getStandingsV3_lck.json"))
        val lec = buildStandingsRows(fixture("getStandingsV3_lec.json"))

        lck.assertUniqueKeys()
        lec.assertUniqueKeys()
        assertTrue(lck.none { it is StandingsRow.MatchRow })
        assertTrue(lec.none { it is StandingsRow.MatchRow })
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.BracketRow::class), lck.ofStage("Play-Ins").map { it::class })
        val regional = lck.ofStage("Regional Championship").filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(10, regional.cards.size)
        assertEquals(listOf("Loser's Bracket - Round 3"), regional.cellNames()[3])
        assertEquals(7, regional.lines.size)
        val playoffs = lec.ofStage("Playoffs").filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(8, playoffs.cards.size)
        assertEquals(7, playoffs.lines.size)
    }

    @Test
    fun `matches without teams are drawn as TBD in their place from the start, with their lines`() {
        val semis = listOf(tbd("s1"), tbd("s2"))
        val final = SectionMatch(MatchState.UNSTARTED, Match("f", emptyList(), format = null))
        val section = StandingsSection(
            "Knockouts", emptyList(), semis + final,
            columns = listOf(BracketLayoutColumn(listOf(BracketCell("Semifinals", semis))), BracketLayoutColumn(listOf(BracketCell("Finals", listOf(final))))),
            winnerLinks = listOf(WinnerLink("s1", "f"), WinnerLink("s2", "f")),
        )
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Knockouts", "knockouts", listOf(section)))))

        rows.assertUniqueKeys()
        val bracket = rows.filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(listOf(listOf("Semifinals"), listOf("Finals")), bracket.cellNames())
        assertEquals(listOf("s1", "s2", "f"), bracket.cards.map { it.sectionMatch.match.id })
        assertEquals(listOf("s1" to "f", "s2" to "f"), bracket.lineIds())
        assertTrue(bracket.card("s1").sectionMatch.match.teams.all { it.code == "TBD" })
        assertFalse(bracket.card("f").hasBothTeams)
        assertFalse(bracket.card("f").isNavigable)
    }

    @Test
    fun `a bracket section without columns falls back to the match list`() {
        val matches = listOf(won("q1", "a", "b"), pending("q2", "c", "d"), pending("f", "a", "c"))
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Knockouts", "knockouts", listOf(StandingsSection("Knockouts", emptyList(), matches))))))

        assertTrue(rows.none { it is StandingsRow.BracketRow || it is StandingsRow.SwissRow })
        assertEquals(matches.map { it.match.id }, rows.filterIsInstance<StandingsRow.MatchRow>().map { it.sectionMatch.match.id })
    }

    @Test
    fun `a bracket section with neither columns nor matches shows an empty section`() {
        val stage = StandingsStage("Playoffs", "playoffs", listOf(StandingsSection("Playoffs", emptyList(), emptyList())))
        val rows = buildStandingsRows(Standings(listOf(stage)))

        assertEquals(1, rows.count { it is StandingsRow.EmptySection })
        assertTrue(rows.none { it is StandingsRow.BracketRow || it is StandingsRow.MatchRow })
    }

    @Test
    fun `ranked sections keep their tables and get no bracket`() {
        val rows = buildStandingsRows(fixture("getStandingsV3_lck.json"))

        assertEquals(2, rows.count { it is StandingsRow.TableHeader })
        assertEquals(10, rows.count { it is StandingsRow.RankingRow })
        val groups = rows.ofStage("Groups")
        assertTrue(groups.none { it is StandingsRow.BracketRow || it is StandingsRow.MatchRow })
    }

    @Test
    fun `with no spoilers the bracket hides scores and winners but still shows who advanced`() {
        val bracket = msi.ofStage("Knockouts").filterIsInstance<StandingsRow.BracketRow>().single()
        val completed = bracket.cards.filter { it.sectionMatch.state == MatchState.COMPLETED }
        assertEquals(14, completed.size)

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
        // The final lists the teams that advanced, whatever the mode.
        assertTrue(bracket.columns.last().cells.single().matches.single().sectionMatch.match.teams.none { it.code == "TBD" })
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

    private fun tbd(id: String): SectionMatch {
        val placeholder = Team("TBD", "TBD", null, null, null, id = "0")
        return SectionMatch(MatchState.UNSTARTED, Match(id, listOf(placeholder, placeholder), format = null))
    }
}
