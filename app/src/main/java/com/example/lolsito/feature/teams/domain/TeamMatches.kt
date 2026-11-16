package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.groupEventsByDay
import java.time.ZoneId

/** The international tournaments whose schedule is searched for every team, whatever its home league. */
object InternationalLeagues {
    const val WORLDS = "98767975604431411"
    const val MSI = "98767991325878492"
    const val FIRST_STAND = "113464388705111224"

    val ids: List<String> = listOf(WORLDS, MSI, FIRST_STAND)
}

/**
 * The schedule filter for a team's matches: its home league plus the international leagues, sorted
 * and without repeats, so the same team always asks with the same filter (page tokens belong to it).
 */
fun teamScheduleLeagueIds(homeLeagueId: String?): List<String> =
    (listOfNotNull(homeLeagueId) + InternationalLeagues.ids).distinct().sorted()

/** The id of the league named [homeLeagueName] (getTeams only sends the name), or null when none matches. */
fun homeLeagueId(leagues: List<League>, homeLeagueName: String?): String? {
    val name = homeLeagueName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return leagues.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }?.id
}

/** The schedule cannot filter by team, so a match is the team's when one of its teams has the team's name. */
fun ScheduleEvent.isPlayedBy(teamName: String): Boolean {
    val name = teamName.trim()
    return match?.teams?.any { it.name.trim().equals(name, ignoreCase = true) } == true
}

/** A team's matches by day: [upcoming] soonest first, [previous] most recent first. */
data class TeamMatches(val upcoming: List<ScheduleDay>, val previous: List<ScheduleDay>)

/**
 * Keeps the events played by [teamName] and splits them: unstarted and live matches are upcoming,
 * everything else (finished or unknown) is previous.
 */
fun buildTeamMatches(events: List<ScheduleEvent>, teamName: String, zoneId: ZoneId): TeamMatches {
    val (upcoming, previous) = events
        .filter { it.isPlayedBy(teamName) }
        .partition { it.state == MatchState.UNSTARTED || it.state == MatchState.IN_PROGRESS }
    return TeamMatches(
        upcoming = groupEventsByDay(upcoming, zoneId, keyPrefix = "upcoming:"),
        previous = groupEventsByDay(previous, zoneId, newestFirst = true, keyPrefix = "previous:"),
    )
}

/**
 * The team's next match: the soonest unstarted or live event of [teamName] with two known teams, or null.
 * A live match started before any unstarted one, so it comes first.
 */
fun nextMatch(events: List<ScheduleEvent>, teamName: String): ScheduleEvent? =
    events
        .filter {
            (it.state == MatchState.UNSTARTED || it.state == MatchState.IN_PROGRESS) &&
                it.match?.teams?.size == 2 && it.isPlayedBy(teamName)
        }
        .minByOrNull { it.startTime }

/** The team of [event] that is not [teamName]; null when the match has no other team. */
fun opponentOf(event: ScheduleEvent, teamName: String): Team? {
    val name = teamName.trim()
    val teams = event.match?.teams.orEmpty()
    val own = teams.indexOfFirst { it.name.trim().equals(name, ignoreCase = true) }
    return if (own < 0) null else teams.filterIndexed { i, _ -> i != own }.firstOrNull()
}
