package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.standings.domain.BracketCell
import com.example.lolsito.feature.standings.domain.BracketLayoutColumn
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.WinnerLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The favorite mark of Standings: which table rows and bracket cards get the star. */
class StandingsFavoritesTest {

    private fun team(name: String, outcome: MatchOutcome? = null, wins: Int? = null) =
        Team(name, name.take(3).uppercase(), null, outcome, wins, id = "id-$name")

    private val finished = SectionMatch(
        MatchState.COMPLETED,
        Match("m1", listOf(team("Alpha", MatchOutcome.WIN, 2), team("Beta", MatchOutcome.LOSS, 0)), null),
    )

    private val semifinal = SectionMatch(
        MatchState.COMPLETED,
        Match("m0", listOf(team("Alpha", MatchOutcome.WIN, 2), team("Gamma", MatchOutcome.LOSS, 1)), null),
    )

    private val rows = buildStandingsRows(
        Standings(
            listOf(
                StandingsStage(
                    "Groups", "groups",
                    listOf(StandingsSection("Group", listOf(Ranking(1, team("Alpha"), 2, 0), Ranking(2, team("Beta"), 0, 2)), emptyList())),
                ),
                StandingsStage(
                    "Playoffs", "playoffs",
                    listOf(
                        StandingsSection(
                            "Bracket",
                            emptyList(),
                            listOf(semifinal, finished),
                            columns = listOf(
                                BracketLayoutColumn(listOf(BracketCell("Semifinals", listOf(semifinal)))),
                                BracketLayoutColumn(listOf(BracketCell("Finals", listOf(finished)))),
                            ),
                            winnerLinks = listOf(WinnerLink("m0", "m1")),
                        ),
                    ),
                ),
            ),
        ),
    )

    private val rankingRows = rows.filterIsInstance<StandingsRow.RankingRow>()
    private val card = rows.filterIsInstance<StandingsRow.BracketRow>().single().columns.last().cells.single().matches.single()

    @Test
    fun `a table row is marked when its team is a favorite, by name`() {
        val favorites = listOf(" alpha ")

        assertEquals(listOf(true, false), rankingRows.map { it.isFavorite(favorites) })
    }

    @Test
    fun `a bracket card marks the team that is a favorite, by name`() {
        val favorites = listOf("BETA")

        assertFalse(card.isFavorite(0, favorites))
        assertTrue(card.isFavorite(1, favorites))
    }

    @Test
    fun `without favorites nothing is marked`() {
        assertTrue(rankingRows.none { it.isFavorite(emptyList()) })
        assertFalse(card.isFavorite(0, emptyList()))
        assertFalse(card.isFavorite(1, emptyList()))
    }

    @Test
    fun `a team slot the card does not have is never marked`() {
        assertFalse(card.isFavorite(2, listOf("Alpha", "Beta")))
    }

    @Test
    fun `a favorite does not change what no spoilers hides on its card`() {
        val favorites = listOf("Alpha")

        assertTrue(card.isFavorite(0, favorites))
        val hidden = card.displayedResult(noSpoilers = true)
        assertNull(hidden.score)
        assertNull(hidden.winnerIndex)
        assertNull(card.displayedGameWins(0, noSpoilers = true))
        assertEquals(card.result, card.displayedResult(noSpoilers = false))
    }
}
