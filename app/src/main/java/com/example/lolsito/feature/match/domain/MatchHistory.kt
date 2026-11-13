package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.feature.teams.domain.isPlayedBy
import com.example.lolsito.feature.teams.domain.teamScheduleLeagueIds
import java.time.LocalDate
import java.time.ZoneId

/** How many finished series a team's recent form shows. */
const val RECENT_FORM_SIZE = 5

/** How many previous meetings the head to head shows. */
const val HEAD_TO_HEAD_SIZE = 5

/** How many pages older than the first one may be read to complete a team's recent form. */
const val MAX_OLDER_HISTORY_PAGES = 2

enum class FormOutcome { WIN, LOSS }

/** One finished series of a team's recent form: how it ended for the team and who it was against. */
data class FormEntry(val matchId: String, val outcome: FormOutcome, val opponentCode: String)

/** A team of the current match with its recent form, most recent series first. */
data class TeamForm(val team: Team, val entries: List<FormEntry>)

/**
 * A finished series between the two teams of the current match. [score] and [winnerIndex] follow the
 * order of the current match's teams (not the order that series listed them in); both are null when unknown.
 */
data class Meeting(
    val matchId: String,
    val date: LocalDate,
    val leagueName: String,
    val score: String?,
    val winnerIndex: Int?,
)

/** What Match detail shows below the games: one [TeamForm] per team and their previous [meetings], most recent first. */
data class MatchHistory(val form: List<TeamForm>, val meetings: List<Meeting>)

/**
 * The schedule filter for the history of two teams: the home league of each (null when unknown) plus the
 * international leagues, sorted and without repeats, so one request covers both teams and page tokens stay valid.
 */
fun matchHistoryLeagueIds(homeLeagueIds: List<String?>): List<String> =
    (homeLeagueIds.flatMap(::teamScheduleLeagueIds) + InternationalLeagues.ids).distinct().sorted()

/**
 * The last [RECENT_FORM_SIZE] finished series of [teamName] in [events], most recent first, never the current match.
 * The team is recognized by name ([isPlayedBy]). A series whose winner is unknown is neither a win nor a loss, so
 * it is left out.
 */
fun recentForm(events: List<ScheduleEvent>, teamName: String, currentMatchId: String): List<FormEntry> =
    events.finishedSeriesOf(teamName, currentMatchId)
        .mapNotNull { event ->
            val match = event.match ?: return@mapNotNull null
            val own = match.teams.indexOfTeam(teamName)
            val winner = formatMatchResult(event.state, match.teams, match.format).winnerIndex ?: return@mapNotNull null
            FormEntry(match.id, if (winner == own) FormOutcome.WIN else FormOutcome.LOSS, match.teams[1 - own].code)
        }
        .take(RECENT_FORM_SIZE)

/**
 * The last [HEAD_TO_HEAD_SIZE] finished series in [events] played by both teams, most recent first, never the
 * current match. Dates are those of [zoneId].
 */
fun headToHead(
    events: List<ScheduleEvent>,
    firstTeamName: String,
    secondTeamName: String,
    currentMatchId: String,
    zoneId: ZoneId,
): List<Meeting> =
    events.finishedSeriesOf(firstTeamName, currentMatchId)
        .filter { it.isPlayedBy(secondTeamName) }
        .mapNotNull { event ->
            val match = event.match ?: return@mapNotNull null
            val first = match.teams.indexOfTeam(firstTeamName)
            val second = match.teams.indexOfTeam(secondTeamName, startingAt = 1 - first)
            if (first == second) return@mapNotNull null
            val result = formatMatchResult(event.state, listOf(match.teams[first], match.teams[second]), match.format)
            Meeting(
                matchId = match.id,
                date = event.startTime.atZone(zoneId).toLocalDate(),
                leagueName = event.leagueName,
                score = result.score,
                winnerIndex = result.winnerIndex,
            )
        }
        .take(HEAD_TO_HEAD_SIZE)

/** True while one of the teams has fewer than [RECENT_FORM_SIZE] series in its recent form: an older page may complete it. */
fun needsOlderHistory(events: List<ScheduleEvent>, teamNames: List<String>, currentMatchId: String): Boolean =
    teamNames.any { recentForm(events, it, currentMatchId).size < RECENT_FORM_SIZE }

/** The recent form of each of the current match's [teams], in their order, and the head to head of the first two. */
fun buildMatchHistory(
    events: List<ScheduleEvent>,
    teams: List<Team>,
    currentMatchId: String,
    zoneId: ZoneId,
): MatchHistory = MatchHistory(
    form = teams.map { TeamForm(it, recentForm(events, it.name, currentMatchId)) },
    meetings = if (teams.size < 2) emptyList() else headToHead(events, teams[0].name, teams[1].name, currentMatchId, zoneId),
)

/**
 * The completed two-team matches played by [teamName] before the current match, most recent first. When the
 * current match is not among the events its start is unknown, so every completed match counts.
 */
private fun List<ScheduleEvent>.finishedSeriesOf(teamName: String, currentMatchId: String): List<ScheduleEvent> {
    val currentStart = firstOrNull { it.matchId == currentMatchId }?.startTime
    return filter { event ->
        (currentStart == null || event.startTime.isBefore(currentStart)) &&
            event.state == MatchState.COMPLETED &&
            event.match?.teams?.size == 2 &&
            event.matchId != currentMatchId &&
            event.isPlayedBy(teamName)
    }.sortedByDescending { it.startTime }
}

/**
 * Index (0 or 1) of the team named [teamName] the way [isPlayedBy] compares names, looking at [startingAt] first:
 * that keeps two teams of the same name apart.
 */
private fun List<Team>.indexOfTeam(teamName: String, startingAt: Int = 0): Int {
    val name = teamName.trim()
    return if (this[startingAt].name.trim().equals(name, ignoreCase = true)) startingAt else 1 - startingAt
}
