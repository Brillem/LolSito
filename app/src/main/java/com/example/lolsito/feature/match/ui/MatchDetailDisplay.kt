package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.match.domain.GameStatsLink
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.feature.match.domain.WatchLink

/**
 * What Match detail shows of the result. With [MatchDetailUiState.Content.resultsHidden] the score, the winner and
 * every game's state (and what gives it away: the Watch and stats buttons, the count of games played) are left out;
 * "Watch live" and the series format stay.
 */
val MatchDetailUiState.Content.displayedResult: MatchResultDisplay
    get() = if (resultsHidden) detail.result.copy(score = null, winnerIndex = null) else detail.result

/** Center text of the header: the score, "LIVE" for a series in progress, otherwise "vs". */
val MatchDetailUiState.Content.headerLabel: String
    get() = displayedResult.score ?: if (headerIsLive) "LIVE" else "vs"

val MatchDetailUiState.Content.headerIsLive: Boolean
    get() = !resultsHidden && displayedResult.score == null && detail.state == MatchState.IN_PROGRESS

val MatchDetailUiState.Content.showsGameState: Boolean get() = !resultsHidden

/** Hidden results list every game, unneeded ones too: how many were played would give the score away. */
val MatchDetailUiState.Content.displayedGames: List<MatchGame>
    get() = if (resultsHidden) detail.games else detail.visibleGames

val MatchDetailUiState.Content.displayedGameKeys: List<String>
    get() = if (resultsHidden) detail.keysOf(detail.games) else detail.gameKeys

fun MatchDetailUiState.Content.watchLinkFor(game: MatchGame): WatchLink? = game.vodLink.takeIf { !resultsHidden }

fun MatchDetailUiState.Content.statsLinkFor(game: MatchGame): GameStatsLink? =
    game.statsLink(detail.teams).takeIf { !resultsHidden }

/** "Watch live" is not a result, so it stays while hidden. */
val MatchDetailUiState.Content.liveLink: WatchLink? get() = detail.liveLink

/** Id to open this team's profile with, or null when it is not tappable: no id, or a "TBD" placeholder. */
fun Team.profileId(): String? = id?.takeIf { it.isNotBlank() && it != "0" && code != "TBD" }

/** The games list (title included) is left out until the series starts: before that every game only says "Upcoming". */
val MatchDetailUiState.Content.showsGames: Boolean get() = detail.state != MatchState.UNSTARTED

/** The head to head is context for a series still to be decided, so a finished series leaves it out. */
val MatchDetailUiState.Content.showsHeadToHead: Boolean get() = detail.state != MatchState.COMPLETED
