package com.example.lolsito.feature.match.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.EventDetailsResponse
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchDetailMappersTest {

    private fun decode(json: String) = LolEsportsJson.decodeFromString<EventDetailsResponse>(json)

    private val detail = decode(Fixtures.read("getEventDetails.json")).toMatchDetail()!!

    @Test
    fun `maps match id, league and best of`() {
        assertEquals("115660588583040710", detail.matchId)
        assertEquals("Worlds", detail.leagueName)
        assertEquals("worlds", detail.leagueSlug)
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 5), detail.format)
        assertEquals(
            "https://static.lolesports.com/leagues/1592594612171_WorldsDarkBG.png",
            detail.leagueImageUrl,
        )
    }

    @Test
    fun `maps teams with codes, ids, score and https logos`() {
        assertEquals(listOf("MVK", "FUR"), detail.teams.map { it.code })
        assertEquals(listOf("107251245690956393", "100205576309502431"), detail.teams.map { it.id })
        assertEquals(listOf(3, 0), detail.teams.map { it.gameWins })
        assertTrue(detail.teams.all { it.outcome == null })
        assertEquals("https://static.lolesports.com/teams/1767089709161_White_Logo.png", detail.teams[0].imageUrl)
        assertEquals("https://static.lolesports.com/teams/FURIA---black.png", detail.teams[1].imageUrl)
    }

    @Test
    fun `maps games with numbers, states and sides`() {
        assertEquals(listOf(1, 2, 3, 4, 5), detail.games.map { it.number })
        assertEquals(
            listOf(GameState.COMPLETED, GameState.COMPLETED, GameState.COMPLETED, GameState.UNNEEDED, GameState.UNNEEDED),
            detail.games.map { it.state },
        )
        assertEquals("115660588583040711", detail.games[0].id)
        assertEquals(GameSide.BLUE, detail.games[0].sideOf("107251245690956393"))
        assertEquals(GameSide.RED, detail.games[0].sideOf("100205576309502431"))
        assertEquals(GameSide.RED, detail.games[3].sideOf("107251245690956393"))
        assertEquals(listOf(1, 2, 3), detail.visibleGames.map { it.number })
    }

    @Test
    fun `series state is completed when every game is completed or unneeded`() {
        assertEquals(MatchState.COMPLETED, detail.state)
    }

    @Test
    fun `series state is derived from game states`() {
        fun stateOf(vararg states: String): MatchState {
            val games = states.mapIndexed { i, s -> """{"number":${i + 1},"id":"g$i","state":"$s"}""" }
            return decode("""{"data":{"event":{"id":"1","match":{"games":[${games.joinToString(",")}]}}}}""")
                .toMatchDetail()!!.state
        }
        assertEquals(MatchState.UNSTARTED, stateOf("unstarted", "unstarted"))
        assertEquals(MatchState.IN_PROGRESS, stateOf("completed", "inProgress", "unstarted"))
        assertEquals(MatchState.IN_PROGRESS, stateOf("completed", "unstarted"))
        assertEquals(MatchState.UNKNOWN, stateOf())
    }

    private fun seriesOf(
        games: List<String>,
        bestOf: Int? = null,
        results: List<String> = emptyList(),
        strategyType: String = "bestOf",
    ): com.example.lolsito.feature.match.domain.MatchDetail {
        val gamesJson = games.mapIndexed { i, s -> """{"number":${i + 1},"id":"g$i","state":"$s"}""" }
        val teamsJson = results.mapIndexed { i, r -> """{"id":"t$i","name":"Team $i","code":"T$i","result":$r}""" }
        val strategy = bestOf?.let { """{"type":"$strategyType","count":$it}""" } ?: "null"
        return decode(
            """{"data":{"event":{"id":"1","match":{"strategy":$strategy,"teams":[${teamsJson.joinToString(",")}],""" +
                """"games":[${gamesJson.joinToString(",")}]}}}}"""
        ).toMatchDetail()!!
    }

    @Test
    fun `finished Bo5 at 3-1 with a leftover unstarted game is completed with score and winner`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "completed", "completed", "unstarted"),
            bestOf = 5,
            results = listOf("""{"outcome":null,"gameWins":3}""", """{"outcome":null,"gameWins":1}"""),
        )
        assertEquals(MatchState.COMPLETED, series.state)
        assertEquals("3 - 1", series.result.score)
        assertEquals(0, series.result.winnerIndex)
    }

    @Test
    fun `series is completed when game wins reach the threshold even with games in progress`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "inProgress"),
            bestOf = 3,
            results = listOf("""{"gameWins":0}""", """{"gameWins":2}"""),
        )
        assertEquals(MatchState.COMPLETED, series.state)
        assertEquals(1, series.result.winnerIndex)
    }

    @Test
    fun `series is completed when a team has a win or loss outcome`() {
        val win = seriesOf(
            games = listOf("completed", "unstarted"),
            results = listOf("""{"outcome":"win","gameWins":null}""", """{"outcome":null,"gameWins":null}"""),
        )
        assertEquals(MatchState.COMPLETED, win.state)
        assertEquals(0, win.result.winnerIndex)

        val loss = seriesOf(
            games = listOf("unstarted", "unstarted"),
            results = listOf("""{"outcome":"loss"}""", """{"outcome":null}"""),
        )
        assertEquals(MatchState.COMPLETED, loss.state)
        assertEquals(1, loss.result.winnerIndex)
    }

    @Test
    fun `game wins below the threshold keep the per-game derivation`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "unstarted", "unstarted", "unstarted"),
            bestOf = 5,
            results = listOf("""{"gameWins":1}""", """{"gameWins":1}"""),
        )
        assertEquals(MatchState.IN_PROGRESS, series.state)
        assertNull(series.result.score)
    }

    @Test
    fun `game wins without best of do not imply completion`() {
        val series = seriesOf(
            games = listOf("completed", "unstarted"),
            results = listOf("""{"gameWins":1}""", """{"gameWins":0}"""),
        )
        assertEquals(MatchState.IN_PROGRESS, series.state)
    }

    @Test
    fun `series with only unneeded games and no results is unknown`() {
        assertEquals(MatchState.UNKNOWN, seriesOf(games = listOf("unneeded", "unneeded", "unneeded")).state)
        assertEquals(
            MatchState.UNKNOWN,
            seriesOf(
                games = listOf("unneeded", "unneeded"),
                bestOf = 3,
                results = listOf("""{"outcome":null,"gameWins":0}""", """{"outcome":null,"gameWins":0}"""),
            ).state,
        )
    }

    @Test
    fun `series without results keeps the per-game derivation`() {
        assertEquals(MatchState.IN_PROGRESS, seriesOf(listOf("completed", "inProgress", "unstarted"), bestOf = 3).state)
        assertEquals(MatchState.IN_PROGRESS, seriesOf(listOf("completed", "unstarted"), bestOf = 3).state)
        assertEquals(MatchState.UNSTARTED, seriesOf(listOf("unstarted", "unstarted"), bestOf = 3).state)
        assertEquals(MatchState.COMPLETED, seriesOf(listOf("completed", "completed", "unneeded"), bestOf = 3).state)
        assertEquals(MatchState.UNKNOWN, seriesOf(emptyList(), bestOf = 3).state)
    }

    @Test
    fun `maps the play all strategy type`() {
        val series = seriesOf(games = listOf("unstarted", "unstarted"), bestOf = 2, strategyType = "playAll")
        assertEquals(SeriesFormat(SeriesType.PLAY_ALL, 2), series.format)
        assertEquals("2 games", series.result.formatLabel)
    }

    @Test
    fun `play all series is not decided early by a best of threshold`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "inProgress"),
            bestOf = 3,
            strategyType = "playAll",
            results = listOf("""{"gameWins":2}""", """{"gameWins":0}"""),
        )
        assertEquals(MatchState.IN_PROGRESS, series.state)
        assertNull(series.result.score)
        assertNull(series.result.winnerIndex)
    }

    @Test
    fun `play all series is completed once game wins add up to the game count`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "unstarted"),
            bestOf = 2,
            strategyType = "playAll",
            results = listOf("""{"gameWins":1}""", """{"gameWins":1}"""),
        )
        assertEquals(MatchState.COMPLETED, series.state)
        assertEquals("1 - 1", series.result.score)
        assertNull(series.result.winnerIndex)
    }

    @Test
    fun `play all series is completed when every game is completed`() {
        val series = seriesOf(
            games = listOf("completed", "completed"),
            bestOf = 2,
            strategyType = "playAll",
            results = listOf("""{"gameWins":2}""", """{"gameWins":0}"""),
        )
        assertEquals(MatchState.COMPLETED, series.state)
        assertEquals(0, series.result.winnerIndex)
    }

    @Test
    fun `unknown series type uses no early threshold`() {
        val series = seriesOf(
            games = listOf("completed", "completed", "unstarted"),
            bestOf = 3,
            strategyType = "swiss",
            results = listOf("""{"gameWins":2}""", """{"gameWins":0}"""),
        )
        assertEquals(MatchState.IN_PROGRESS, series.state)
    }

    @Test
    fun `tolerates missing and null fields`() {
        val minimal = decode(
            """{"data":{"event":{"id":"42","league":null,"match":{"strategy":null,
            |"teams":[{"name":null,"code":"","image":null,"result":null}],
            |"games":[{"number":null,"id":null,"state":"weird","teams":[{"id":null,"side":"green"}]}]}}}}""".trimMargin()
        ).toMatchDetail()!!

        assertEquals("42", minimal.matchId)
        assertEquals("", minimal.leagueName)
        assertNull(minimal.format)
        assertNull(minimal.blockName)
        assertEquals("TBD", minimal.teams.single().code)
        assertNull(minimal.teams.single().imageUrl)
        assertNull(minimal.teams.single().gameWins)
        val game = minimal.games.single()
        assertEquals(1, game.number)
        assertEquals(GameState.UNKNOWN, game.state)
        assertNull(game.sideOf("x"))
    }

    @Test
    fun `returns null without an event or a match`() {
        assertNull(decode("""{"data":{}}""").toMatchDetail())
        assertNull(decode("""{"data":{"event":{"id":"1"}}}""").toMatchDetail())
        assertNull(decode("""{"data":{"event":{"id":"","match":{}}}}""").toMatchDetail())
    }
}
