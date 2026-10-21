package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import org.junit.Assert.assertEquals
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
}
