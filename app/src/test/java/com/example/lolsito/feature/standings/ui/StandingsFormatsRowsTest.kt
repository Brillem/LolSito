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
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.placeholder
import com.example.lolsito.feature.standings.domain.swissSection
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsFormatsRowsTest {

    private fun fixture(name: String): Standings =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(name)).toDomainStandings()

    private val worlds by lazy { buildStandingsRows(fixture("getStandingsV3_worlds2026.json")) }

    private val swiss by lazy { worlds.filterIsInstance<StandingsRow.SwissRow>().single() }

    @Test
    fun `the worlds swiss shows the api rounds grouped by record`() {
        assertEquals(listOf("Round 1", "Round 2", "Round 3", "Round 4", "Round 5"), swiss.rounds.map { it.name })
        assertEquals(listOf(8, 8, 8, 6, 3), swiss.rounds.map { it.matches.size })
        assertEquals(
            listOf(listOf("0-0"), listOf("1-0", "0-1"), listOf("2-0", "1-1", "0-2"), listOf("2-1", "1-2"), listOf("2-2")),
            swiss.rounds.map { round -> round.displayedGroups(noSpoilers = false).map { it.record } },
        )
        swiss.rounds.forEach { round ->
            assertEquals(round.matches.toSet(), round.displayedGroups(noSpoilers = false).flatMap { it.matches }.toSet())
        }
    }

    @Test
    fun `a swiss whose records cannot be worked out shows only its rounds`() {
        val section = fixture("getStandingsV3_worlds2026.json").stages.single { it.name == "Swiss" }.sections.single()
        val unpaired = section.columns.map { column -> column.cells.single().matches.map { placeholder(it.match.id) } }
        val rows = buildStandingsRows(Standings(listOf(StandingsStage("Swiss", "swiss", listOf(swissSection(unpaired))))))

        val row = rows.filterIsInstance<StandingsRow.SwissRow>().single()
        assertTrue(rows.none { it is StandingsRow.MatchRow })
        assertEquals(listOf(8, 8, 8, 6, 3), row.rounds.map { it.matches.size })
        row.rounds.forEach { round ->
            val groups = round.displayedGroups(noSpoilers = false)
            assertEquals(1, groups.size)
            assertNull(groups.single().record)
            assertEquals(round.matches, groups.single().matches)
        }
    }

    @Test
    fun `with no spoilers the swiss shows its rounds without records, in list order`() {
        swiss.rounds.forEach { round ->
            val groups = round.displayedGroups(noSpoilers = true)
            assertEquals(1, groups.size)
            assertNull(groups.single().record)
            // List order, not record order, so the layout gives nothing away.
            assertEquals(round.matches, groups.single().matches)
        }
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

        val lck = buildStandingsRows(fixture("getStandingsV3_lck.json")).filterIsInstance<StandingsRow.RankingRow>()
        assertEquals(10, lck.size)
        assertEquals(lck.map { it.ranking.team.id }, lck.map { it.teamId })
        assertTrue(lck.all { it.teamId != null })
    }
}
