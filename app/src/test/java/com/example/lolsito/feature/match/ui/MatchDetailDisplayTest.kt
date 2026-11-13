package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.feature.match.domain.WatchLink
import com.example.lolsito.feature.match.domain.WatchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchDetailDisplayTest {

    private val vod = WatchLink(WatchProvider.YOUTUBE, "vod123", null)
    private val stream = WatchLink(WatchProvider.TWITCH, "lolesports", null)

    private fun detail(state: MatchState = MatchState.COMPLETED) = MatchDetail(
        matchId = "m",
        state = state,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        leagueImageUrl = null,
        blockName = "Finals",
        teams = listOf(
            Team("T1", "T1", null, MatchOutcome.WIN, 2, id = "a"),
            Team("Gen.G", "GEN", null, MatchOutcome.LOSS, 0, id = "b"),
        ),
        format = SeriesFormat(SeriesType.BEST_OF, 3),
        games = listOf(
            MatchGame(1, "g1", GameState.COMPLETED, mapOf("a" to GameSide.BLUE), vod = vod),
            MatchGame(2, "g2", GameState.COMPLETED, mapOf("a" to GameSide.RED), vod = vod),
            MatchGame(3, "g3", GameState.UNNEEDED, emptyMap()),
        ),
        liveStream = stream,
    )

    private fun content(hidden: Boolean, state: MatchState = MatchState.COMPLETED) =
        MatchDetailUiState.Content(detail(state), resultsHidden = hidden)

    @Test
    fun `with results shown everything is as the detail says`() {
        val content = content(hidden = false)

        assertEquals(content.detail.result, content.displayedResult)
        assertEquals("2 - 0", content.displayedResult.score)
        assertEquals("2 - 0", content.headerLabel)
        assertEquals(content.detail.visibleGames, content.displayedGames)
        assertEquals(content.detail.gameKeys, content.displayedGameKeys)
        assertEquals(true, content.showsGameState)
        val game = content.detail.games[0]
        assertEquals(vod, content.watchLinkFor(game))
        assertNotNull(content.statsLinkFor(game))
    }

    @Test
    fun `with results hidden the score and the loser are gone but the format stays`() {
        val content = content(hidden = true)

        assertNull(content.displayedResult.score)
        assertNull(content.displayedResult.winnerIndex)
        assertEquals(TeamEmphasis.NEUTRAL, content.displayedResult.emphasisFor(0))
        assertEquals(TeamEmphasis.NEUTRAL, content.displayedResult.emphasisFor(1))
        assertEquals("Bo3", content.displayedResult.formatLabel)
        assertEquals("vs", content.headerLabel)
        assertEquals(false, content.headerIsLive)
        assertEquals(false, content.showsGameState)
    }

    @Test
    fun `with results hidden every game is listed so the count does not tell the score`() {
        val content = content(hidden = true)

        assertEquals(listOf(1, 2, 3), content.displayedGames.map { it.number })
        assertEquals(content.displayedGames.size, content.displayedGameKeys.size)
        assertEquals(content.displayedGameKeys.size, content.displayedGameKeys.toSet().size)
    }

    @Test
    fun `with results hidden no game offers watch or stats`() {
        val content = content(hidden = true)

        content.detail.games.forEach { game ->
            assertNull(content.watchLinkFor(game))
            assertNull(content.statsLinkFor(game))
        }
    }

    @Test
    fun `watch live stays while the series is in progress even when hidden`() {
        val hidden = content(hidden = true, state = MatchState.IN_PROGRESS)

        assertEquals(stream, hidden.liveLink)
        assertEquals("vs", hidden.headerLabel)
        assertEquals(false, hidden.headerIsLive)
    }

    @Test
    fun `an in progress series shows LIVE in the header when results are shown`() {
        val shown = content(hidden = false, state = MatchState.IN_PROGRESS)

        assertEquals("LIVE", shown.headerLabel)
        assertEquals(true, shown.headerIsLive)
    }

    @Test
    fun `an unstarted series reads vs whether hidden or not`() {
        assertEquals("vs", content(hidden = false, state = MatchState.UNSTARTED).headerLabel)
        assertEquals("vs", content(hidden = true, state = MatchState.UNSTARTED).headerLabel)
    }

    @Test
    fun `a series that has not started lists no games`() {
        assertEquals(false, MatchDetailUiState.Content(detail(MatchState.UNSTARTED)).showsGames)
        assertEquals(false, MatchDetailUiState.Content(detail(MatchState.UNSTARTED), resultsHidden = true).showsGames)
    }

    @Test
    fun `a series in progress or finished lists its games`() {
        assertEquals(true, MatchDetailUiState.Content(detail(MatchState.IN_PROGRESS)).showsGames)
        assertEquals(true, MatchDetailUiState.Content(detail(MatchState.COMPLETED)).showsGames)
        assertEquals(true, MatchDetailUiState.Content(detail(MatchState.COMPLETED), resultsHidden = true).showsGames)
    }

    @Test
    fun `a finished series shows no head to head`() {
        assertEquals(false, MatchDetailUiState.Content(detail(MatchState.COMPLETED)).showsHeadToHead)
    }

    @Test
    fun `a series not played yet or in progress shows the head to head`() {
        assertEquals(true, MatchDetailUiState.Content(detail(MatchState.UNSTARTED)).showsHeadToHead)
        assertEquals(true, MatchDetailUiState.Content(detail(MatchState.IN_PROGRESS)).showsHeadToHead)
    }
}
