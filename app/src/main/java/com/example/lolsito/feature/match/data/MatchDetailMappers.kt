package com.example.lolsito.feature.match.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.dto.EventDetailsResponse
import com.example.lolsito.core.network.dto.GameDto
import com.example.lolsito.core.network.toHttps
import com.example.lolsito.core.network.toSeriesFormat
import com.example.lolsito.core.network.toTeam
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchGame

/** Returns null when the response has no event, no event id, or no match. */
fun EventDetailsResponse.toMatchDetail(): MatchDetail? {
    val event = data.event ?: return null
    val id = event.id?.takeIf { it.isNotBlank() } ?: return null
    val match = event.match ?: return null
    val games = match.games.orEmpty().mapIndexed { index, game -> game.toDomain(index) }
    val teams = match.teams.orEmpty().map { it.toTeam() }
    val format = match.strategy.toSeriesFormat()
    return MatchDetail(
        matchId = id,
        state = seriesState(games, teams, format),
        leagueName = event.league?.name.orEmpty(),
        leagueSlug = event.league?.slug,
        leagueImageUrl = event.league?.image.toHttps(),
        blockName = event.blockName,
        teams = teams,
        format = format,
        games = games,
    )
}

fun String?.toGameState(): GameState = when (this) {
    "unstarted" -> GameState.UNSTARTED
    "inProgress" -> GameState.IN_PROGRESS
    "completed" -> GameState.COMPLETED
    "unneeded" -> GameState.UNNEEDED
    else -> GameState.UNKNOWN
}

private fun String?.toSide(): GameSide? = when (this) {
    "blue" -> GameSide.BLUE
    "red" -> GameSide.RED
    else -> null
}

private fun GameDto.toDomain(index: Int): MatchGame = MatchGame(
    number = number ?: (index + 1),
    id = id?.takeIf { it.isNotBlank() },
    state = state.toGameState(),
    sides = teams.orEmpty().mapNotNull { team ->
        val teamId = team.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val side = team.side.toSide() ?: return@mapNotNull null
        teamId to side
    }.toMap(),
)

/**
 * Series state from team results first, then from game states.
 *
 * Completed when a team has a win/loss outcome or the game wins decide the series (a best-of
 * majority, or every play-all game won; see [SeriesFormat.isDecidedBy]), even if a leftover game
 * is still listed. Otherwise: completed when every game is completed or unneeded and at least
 * one was played; in progress when a game is live or the
 * series has started but not finished; unstarted when no game has been played. A series whose
 * games are all unneeded, with no results, is unknown: nothing proves it was played.
 */
internal fun seriesState(games: List<MatchGame>, teams: List<Team>, format: SeriesFormat?): MatchState {
    if (hasDecidedResult(teams, format)) return MatchState.COMPLETED
    if (games.isEmpty()) return MatchState.UNKNOWN
    val states = games.map { it.state }
    return when {
        states.all { it == GameState.UNNEEDED } -> MatchState.UNKNOWN
        states.all { it == GameState.COMPLETED || it == GameState.UNNEEDED } -> MatchState.COMPLETED
        GameState.IN_PROGRESS in states -> MatchState.IN_PROGRESS
        GameState.COMPLETED in states -> MatchState.IN_PROGRESS
        states.all { it == GameState.UNSTARTED } -> MatchState.UNSTARTED
        else -> MatchState.UNKNOWN
    }
}

private fun hasDecidedResult(teams: List<Team>, format: SeriesFormat?): Boolean =
    teams.any { it.outcome != null } || format?.isDecidedBy(teams.map { it.gameWins }) == true
