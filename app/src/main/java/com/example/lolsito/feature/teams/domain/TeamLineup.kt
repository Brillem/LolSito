package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import kotlinx.coroutines.CancellationException

/** Most finished matches tried, newest first, to find the lineup of a team's last game. */
const val LINEUP_MATCH_LIMIT = 3

/**
 * The lineup of a team: the player ids (the same ids as [Player.id]) of its five players in a game.
 * [Players] are "starters" only in the sense of "played the team's last game", not an official lineup.
 */
interface TeamLineupRepository {
    /**
     * The player ids of [teamId] in the last completed game of match [matchId]; null when that match
     * yields no lineup (no completed game, the game feed has nothing, or the team is not in it).
     */
    suspend fun getLineup(matchId: String, teamId: String): Set<String>?
}

/** Ids of the newest finished matches among [events] (at most [LINEUP_MATCH_LIMIT]), newest first; matches without an id are skipped. */
fun lineupCandidateMatchIds(events: List<ScheduleEvent>): List<String> =
    events
        .filter { it.state == MatchState.COMPLETED }
        .sortedByDescending { it.startTime }
        .mapNotNull { it.matchId }
        .distinct()
        .take(LINEUP_MATCH_LIMIT)

/** Id of the last completed game that has one (the feed has frames only for those), or null. */
fun MatchDetail.lastPlayedGameId(): String? =
    games.lastOrNull { it.state == GameState.COMPLETED && it.id != null }?.id

/**
 * The first non-empty lineup [fetch] gives for [matchIds], in order. A match that fails (anything but
 * a cancellation) or gives nothing is skipped; null when none gives a lineup.
 */
suspend fun findLineup(matchIds: List<String>, fetch: suspend (String) -> Set<String>?): Set<String>? {
    for (matchId in matchIds) {
        val lineup = try {
            fetch(matchId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (!lineup.isNullOrEmpty()) return lineup
    }
    return null
}

/** The roster split in two: [starters] in role order, then [rest] grouped by role as in the roster. */
data class RosterSplit(val starters: List<Player>, val rest: List<RoleGroup>)

/**
 * Splits [roster] by the player ids in [lineup]. Lineup ids that are not in the roster are ignored.
 * Null when no roster player is in the lineup, so the roster stays as it is.
 */
fun splitRoster(roster: List<RoleGroup>, lineup: Set<String>): RosterSplit? {
    fun Player.starts() = id != null && id in lineup
    val starters = roster.flatMap { group -> group.players.filter { it.starts() } }
    if (starters.isEmpty()) return null
    val rest = roster.mapNotNull { group ->
        group.players.filterNot { it.starts() }.takeIf { it.isNotEmpty() }?.let { RoleGroup(group.role, it) }
    }
    return RosterSplit(starters, rest)
}
