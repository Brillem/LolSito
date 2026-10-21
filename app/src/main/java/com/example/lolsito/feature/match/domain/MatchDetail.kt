package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.formatMatchResult

enum class GameState { UNSTARTED, IN_PROGRESS, COMPLETED, UNNEEDED, UNKNOWN }

enum class GameSide { BLUE, RED }

data class MatchGame(
    val number: Int,
    val id: String?,
    val state: GameState,
    /** Side per team id; empty when the API does not send sides. */
    val sides: Map<String, GameSide>,
) {
    fun sideOf(teamId: String?): GameSide? = teamId?.let(sides::get)
}

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
) {
    /** Games that were or will be played; `unneeded` games are hidden. */
    val visibleGames: List<MatchGame> get() = games.filter { it.state != GameState.UNNEEDED }

    /**
     * LazyColumn keys aligned with [visibleGames]. Based on the game id (or the number when the id
     * is missing) plus an occurrence ordinal, so repeated API data never yields duplicate keys.
     */
    val gameKeys: List<String> by lazy {
        val seen = mutableMapOf<String, Int>()
        visibleGames.map { game ->
            val base = game.id?.let { "game:$it" } ?: "game-number:${game.number}"
            val ordinal = seen.getOrDefault(base, 0)
            seen[base] = ordinal + 1
            "$base#$ordinal"
        }
    }

    val result: MatchResultDisplay get() = formatMatchResult(state, teams, format)
}

interface MatchDetailRepository {
    suspend fun getMatchDetail(matchId: String): MatchDetail
}
