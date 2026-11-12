package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.feature.game.domain.GameTeam
import com.example.lolsito.core.model.formatMatchResult

enum class GameState { UNSTARTED, IN_PROGRESS, COMPLETED, UNNEEDED, UNKNOWN }

enum class GameSide { BLUE, RED }

data class MatchGame(
    val number: Int,
    val id: String?,
    val state: GameState,
    /** Side per team id; empty when the API does not send sides. */
    val sides: Map<String, GameSide>,
    /** VOD to watch, already chosen by [pickWatchLink]; null when there is none. */
    val vod: WatchLink? = null,
) {
    /** VOD to offer as "Watch": only for a completed game. */
    val vodLink: WatchLink? get() = vod?.takeIf { state == GameState.COMPLETED }

    fun sideOf(teamId: String?): GameSide? = teamId?.let(sides::get)

    /** Id to open the game stats with; null until the game starts, since the feed has no frames before. */
    val statsGameId: String?
        get() = id?.takeIf { state == GameState.IN_PROGRESS || state == GameState.COMPLETED }

    /**
     * Link to this game's stats ([statsGameId]) with the code and logo of each of the [teams] that has an id
     * and a code. The sides are left to the stats feed, which is the one that says who played where.
     */
    fun statsLink(teams: List<Team>): GameStatsLink? = statsGameId?.let { gameId ->
        GameStatsLink(
            gameId,
            teams.mapNotNull { team ->
                val id = team.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                team.code.takeIf { it.isNotBlank() }?.let { code ->
                    id to GameTeam(code, team.imageUrl?.takeIf { it.isNotBlank() })
                }
            }.toMap(),
        )
    }
}

/** What the game stats screen is opened with: the game plus the code and logo of each team by team id. */
data class GameStatsLink(val gameId: String, val teams: Map<String, GameTeam>)

data class MatchDetail(
    val matchId: String,
    /** Series state derived from its games (event details carry no match state). */
    val state: MatchState,
    val leagueName: String,
    val leagueSlug: String?,
    val leagueImageUrl: String?,
    val blockName: String?,
    val teams: List<Team>,
    val format: SeriesFormat?,
    val games: List<MatchGame>,
    /** Live stream to watch, already chosen by [pickWatchLink]; null when there is none. */
    val liveStream: WatchLink? = null,
) {
    /** Games that were or will be played; `unneeded` games are hidden. */
    val visibleGames: List<MatchGame> get() = games.filter { it.state != GameState.UNNEEDED }

    /**
     * LazyColumn keys aligned with [visibleGames]. Based on the game id (or the number when the id
     * is missing) plus an occurrence ordinal, so repeated API data never yields duplicate keys.
     */
    val gameKeys: List<String> by lazy {
        val unique = UniqueKeys()
        visibleGames.map { game -> unique.next(game.id?.let { "game:$it" } ?: "game-number:${game.number}") }
    }

    /** Stream to offer as "Watch live": only while the series is in progress. */
    val liveLink: WatchLink? get() = liveStream?.takeIf { state == MatchState.IN_PROGRESS }

    val result: MatchResultDisplay get() = formatMatchResult(state, teams, format)
}

interface MatchDetailRepository {
    suspend fun getMatchDetail(matchId: String): MatchDetail
}
