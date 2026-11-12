package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsFormatsRowsTest {

    private fun fixture(name: String): Standings =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(name)).toDomainStandings()

    private val msi by lazy { buildStandingsRows(fixture("getStandings_msi2026.json")) }
    private val worlds by lazy { buildStandingsRows(fixture("getStandings_worlds2026_knockouts.json")) }

    private fun List<StandingsRow>.ofStage(name: String): List<StandingsRow> {
        val start = indexOfFirst { it is StandingsRow.StageHeader && it.name == name }
        val end = drop(start + 1).indexOfFirst { it is StandingsRow.StageHeader }.let { if (it < 0) size else start + 1 + it }
        return subList(start + 1, end)
    }

    /** Every key in the list, including the cards drawn inside bracket and Swiss rows. */
    private fun List<StandingsRow>.allKeys(): List<String> = flatMap { row ->
        listOf(row.key) + when (row) {
            is StandingsRow.BracketRow -> row.rounds.flatMap { c -> c.matches.map { it.key } }
            is StandingsRow.DoubleEliminationRow -> (row.upper + row.lower + row.grandFinal).flatMap { c -> c.matches.map { it.key } }
            is StandingsRow.SwissRow -> row.rounds.flatMap { c -> c.matches.map { it.key } }
            else -> emptyList()
        }
    }

    private fun List<StandingsRow>.assertUniqueKeys() {
        val keys = allKeys()
        assertEquals("duplicate keys in $keys", keys.size, keys.toSet().size)
    }

    private val StandingsRow.DoubleEliminationRow.cards get() = (upper + lower + grandFinal).flatMap { it.matches }

    @Test
    fun `msi sections are drawn as double-elimination brackets`() {
        msi.assertUniqueKeys()
        assertTrue(msi.none { it is StandingsRow.MatchRow })

        val playIns = msi.ofStage("Play-Ins").filterIsInstance<StandingsRow.DoubleEliminationRow>().single()
        assertEquals(listOf("Upper Semifinals", "Upper Final"), playIns.upper.map { it.name })
        assertEquals(listOf(2, 1), playIns.upper.map { it.matches.size })
        assertEquals(listOf(1, 1), playIns.lower.map { it.matches.size })
        assertEquals("Grand Final", playIns.grandFinal.name)
        assertEquals(1, playIns.grandFinal.matches.size)

        val knockouts = msi.ofStage("Knockouts")
        assertEquals(listOf(StandingsRow.EliminationNotice::class, StandingsRow.DoubleEliminationRow::class), knockouts.map { it::class })
        val bracket = knockouts.filterIsInstance<StandingsRow.DoubleEliminationRow>().single()
        assertEquals(listOf(4, 2, 1), bracket.upper.map { it.matches.size })
        assertEquals(listOf(2, 2, 1, 1), bracket.lower.map { it.matches.size })
        assertEquals(listOf(true, true, false), bracket.upper.map { it.linkedToNext })
        assertEquals(listOf(true, true, true, false), bracket.lower.map { it.linkedToNext })
        assertEquals(14, bracket.cards.map { it.sectionMatch.match.id }.toSet().size)
    }

    @Test
    fun `worlds draws play-ins as double elimination, swiss by round and knockouts as before`() {
        worlds.assertUniqueKeys()
        assertTrue(worlds.none { it is StandingsRow.MatchRow })
        assertEquals(1, worlds.ofStage("Play-Ins").count { it is StandingsRow.DoubleEliminationRow })

        val swiss = worlds.ofStage("Swiss").filterIsInstance<StandingsRow.SwissRow>().single()
        assertEquals(listOf("Round 1", "Round 2", "Round 3", "Round 4", "Round 5"), swiss.rounds.map { it.name })
        assertEquals(listOf(8, 8, 8, 6, 3), swiss.rounds.map { it.matches.size })
        assertEquals(
            listOf(listOf("0-0"), listOf("1-0", "0-1"), listOf("2-0", "1-1", "0-2"), listOf("2-1", "1-2"), listOf("2-2")),
            swiss.rounds.map { round -> round.displayedGroups(noSpoilers = false).map { it.record } },
        )
        swiss.rounds.forEach { round ->
            assertEquals(round.matches.toSet(), round.displayedGroups(noSpoilers = false).flatMap { it.matches }.toSet())
        }

        val knockouts = worlds.ofStage("Knockouts").filterIsInstance<StandingsRow.BracketRow>().single()
        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), knockouts.rounds.map { it.name })
    }

    @Test
    fun `an unplayed swiss stays a list while the unplayed play-ins are a double elimination`() {
        val rows = buildStandingsRows(fixture("getStandings_worlds2026.json"))

        rows.assertUniqueKeys()
        val playIns = rows.ofStage("Play-Ins")
        assertEquals(1, playIns.count { it is StandingsRow.DoubleEliminationRow })
        assertTrue(playIns.none { it is StandingsRow.MatchRow })
        val swiss = rows.ofStage("Swiss")
        assertTrue(swiss.none { it is StandingsRow.SwissRow })
        assertEquals(33, swiss.count { it is StandingsRow.MatchRow })
    }

    @Test
    fun `with no spoilers the swiss shows its rounds without records, in list order`() {
        val swiss = worlds.filterIsInstance<StandingsRow.SwissRow>().single()

        swiss.rounds.forEach { round ->
            val groups = round.displayedGroups(noSpoilers = true)
            assertEquals(1, groups.size)
            assertNull(groups.single().record)
            // List order, not record order, so the layout gives nothing away.
            assertEquals(round.matches, groups.single().matches)
        }
    }

    @Test
    fun `with no spoilers double-elimination cards hide scores and winners`() {
        val bracket = msi.filterIsInstance<StandingsRow.DoubleEliminationRow>().last()
        val completed = bracket.cards.filter { it.sectionMatch.state == MatchState.COMPLETED }
        assertEquals(14, completed.size)

        completed.forEach { row ->
            assertNotNull(row.displayedResult(noSpoilers = false).score)
            assertNotNull(row.displayedResult(noSpoilers = false).winnerIndex)
            val hidden = row.displayedResult(noSpoilers = true)
            assertNull(hidden.score)
            assertEquals(TeamEmphasis.NEUTRAL, hidden.emphasisFor(0))
            assertEquals(TeamEmphasis.NEUTRAL, hidden.emphasisFor(1))
            assertNull(row.displayedGameWins(0, noSpoilers = true))
            assertNull(row.displayedGameWins(1, noSpoilers = true))
        }
        // Who advanced is still drawn: the later rounds list real teams.
        assertTrue(bracket.grandFinal.matches.single().sectionMatch.match.teams.none { it.code == "TBD" })
    }

    @Test
    fun `only a match in progress is live`() {
        fun row(state: MatchState) = StandingsRow.MatchRow("k", SectionMatch(state, Match("m", emptyList(), null)))

        assertTrue(row(MatchState.IN_PROGRESS).isLive)
        assertFalse(row(MatchState.COMPLETED).isLive)
        assertFalse(row(MatchState.UNSTARTED).isLive)
    }

    @Test
    fun `ranking rows open the team profile only for a real team id`() {
        fun row(id: String?, code: String = "AAA") = StandingsRow.RankingRow("k", Ranking(1, Team("A", code, null, null, null, id = id), 1, 0))

        assertEquals("123", row("123").teamId)
        assertNull(row(null).teamId)
        assertNull(row("0", code = "TBD").teamId)

        val lck = buildStandingsRows(fixture("getStandings_lck.json")).filterIsInstance<StandingsRow.RankingRow>()
        assertEquals(10, lck.size)
        assertEquals(lck.map { it.ranking.team.id }, lck.map { it.teamId })
        assertTrue(lck.all { it.teamId != null })
    }
}
