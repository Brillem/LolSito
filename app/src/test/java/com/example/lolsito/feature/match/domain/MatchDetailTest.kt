package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchDetailTest {

    private fun game(number: Int, id: String?, state: GameState = GameState.COMPLETED) =
        MatchGame(number = number, id = id, state = state, sides = emptyMap())

    private fun detail(vararg games: MatchGame) = MatchDetail(
        matchId = "m",
        state = MatchState.COMPLETED,
        leagueName = "",
        leagueSlug = null,
        leagueImageUrl = null,
        blockName = null,
        teams = emptyList(),
        format = SeriesFormat(SeriesType.BEST_OF, 5),
        games = games.toList(),
    )

    @Test
    fun `game keys are stable for distinct ids and numbers`() {
        val keys = detail(game(1, "a"), game(2, "b"), game(3, null)).gameKeys
        assertEquals(listOf("game:a#0", "game:b#0", "game-number:3#0"), keys)
    }

    @Test
    fun `game keys stay unique when an id is repeated`() {
        val keys = detail(game(1, "dup"), game(2, "dup"), game(3, "dup")).gameKeys
        assertEquals(listOf("game:dup#0", "game:dup#1", "game:dup#2"), keys)
    }

    @Test
    fun `game keys stay unique when games without id share a number`() {
        val keys = detail(game(1, null), game(1, null), game(2, null)).gameKeys
        assertEquals(listOf("game-number:1#0", "game-number:1#1", "game-number:2#0"), keys)
    }

    @Test
    fun `game keys never collide between an id and a number`() {
        val keys = detail(game(1, "1"), game(1, null)).gameKeys
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `game keys align with visible games`() {
        val d = detail(game(1, "a"), game(2, "b", GameState.UNNEEDED), game(3, "c", GameState.UNSTARTED))
        assertEquals(d.visibleGames.size, d.gameKeys.size)
        assertEquals(listOf("game:a#0", "game:c#0"), d.gameKeys)
    }

    @Test
    fun `started games with an id open their stats`() {
        assertEquals("a", game(1, "a", GameState.IN_PROGRESS).statsGameId)
        assertEquals("b", game(2, "b", GameState.COMPLETED).statsGameId)
    }

    @Test
    fun `games that did not start or have no id stay non-tappable`() {
        assertNull(game(1, "a", GameState.UNSTARTED).statsGameId)
        assertNull(game(2, null, GameState.COMPLETED).statsGameId)
        assertNull(game(3, null, GameState.IN_PROGRESS).statsGameId)
        assertNull(game(4, "d", GameState.UNNEEDED).statsGameId)
        assertNull(game(5, "e", GameState.UNKNOWN).statsGameId)
    }

    private val teams = listOf(
        Team("MVK Esports", "MVK", null, null, null, id = "mvk"),
        Team("FURIA", "FUR", null, null, null, id = "fur"),
    )

    private fun sided(id: String?, state: GameState, vararg sides: Pair<String, GameSide>) =
        MatchGame(number = 1, id = id, state = state, sides = sides.toMap())

    @Test
    fun `stats link carries the code of the team on each side`() {
        val game = sided("g1", GameState.COMPLETED, "mvk" to GameSide.BLUE, "fur" to GameSide.RED)
        assertEquals(GameStatsLink("g1", blueTeamCode = "MVK", redTeamCode = "FUR"), game.statsLink(teams))
    }

    @Test
    fun `stats link follows the sides of that game when teams swap`() {
        val game = sided("g4", GameState.IN_PROGRESS, "fur" to GameSide.BLUE, "mvk" to GameSide.RED)
        assertEquals(GameStatsLink("g4", blueTeamCode = "FUR", redTeamCode = "MVK"), game.statsLink(teams))
    }

    @Test
    fun `stats link has no codes when sides or team ids are unknown`() {
        assertEquals(GameStatsLink("g1", null, null), sided("g1", GameState.COMPLETED).statsLink(teams))
        val noIds = teams.map { it.copy(id = null) }
        val game = sided("g1", GameState.COMPLETED, "mvk" to GameSide.BLUE, "fur" to GameSide.RED)
        assertEquals(GameStatsLink("g1", null, null), game.statsLink(noIds))
    }

    @Test
    fun `stats link skips a side shared by two teams or a blank code`() {
        val bothBlue = sided("g1", GameState.COMPLETED, "mvk" to GameSide.BLUE, "fur" to GameSide.BLUE)
        assertEquals(GameStatsLink("g1", null, null), bothBlue.statsLink(teams))
        val blank = listOf(teams[0].copy(code = " "), teams[1])
        val game = sided("g1", GameState.COMPLETED, "mvk" to GameSide.BLUE, "fur" to GameSide.RED)
        assertEquals(GameStatsLink("g1", null, "FUR"), game.statsLink(blank))
    }

    @Test
    fun `games without stats have no stats link`() {
        val sides = arrayOf("mvk" to GameSide.BLUE, "fur" to GameSide.RED)
        assertNull(sided("g1", GameState.UNSTARTED, *sides).statsLink(teams))
        assertNull(sided(null, GameState.COMPLETED, *sides).statsLink(teams))
    }
}
