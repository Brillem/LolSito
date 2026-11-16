package com.example.lolsito.feature.teams.domain

import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.pickDefaultTournament
import java.time.LocalDate

/**
 * The ranking of the team in [standings]: the one in the LAST stage (API order) that ranks it, matching
 * by [teamId] first and, only when no ranking has that id, by [teamName] (case-insensitive). Null when
 * no stage ranks the team (e.g. bracket-only standings).
 */
fun findTeamRanking(standings: Standings, teamId: String, teamName: String): Ranking? {
    val rankings = standings.stages.asReversed().flatMap { stage -> stage.sections.flatMap { it.rankings } }
    val name = teamName.trim()
    return rankings.firstOrNull { it.team.id == teamId }
        ?: rankings.firstOrNull { it.team.name.trim().equals(name, ignoreCase = true) }
}

/** English ordinal: 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st... */
fun ordinalLabel(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/**
 * The line under a team's header: "3rd in LCK · 12-4". Each part degrades on its own: without an ordinal
 * it is "LCK · 12-4", without a record (wins and losses both known) "3rd in LCK". Null when the
 * [ranking] has neither, so nothing is shown.
 */
fun standingLine(leagueName: String, ranking: Ranking): String? {
    val place = ranking.ordinal?.let { ordinalLabel(it) }
    val wins = ranking.wins
    val losses = ranking.losses
    val record = if (wins != null && losses != null) "$wins-$losses" else null
    if (place == null && record == null) return null
    val league = if (place != null) "$place in $leagueName" else leagueName
    return listOfNotNull(league, record).joinToString(" · ")
}

/**
 * Whether [tournament] is being played on [today]: started (start date not after today) and not over
 * (no end date, or one not before today). A tournament without a start date is not ongoing.
 */
fun isOngoing(tournament: Tournament, today: LocalDate): Boolean {
    val start = tournament.startDate ?: return false
    val end = tournament.endDate
    return !start.isAfter(today) && (end == null || !end.isBefore(today))
}

/**
 * The standing line of a team in its home league's current tournament ([pickDefaultTournament] for
 * [today]), only while that tournament [isOngoing]: a finished tournament's record belongs to the
 * Tournaments tab. Null when there is no ongoing tournament or no ranking for the team; requests
 * that fail throw.
 */
suspend fun StandingsRepository.teamStandingLine(
    leagueId: String,
    leagueName: String,
    teamId: String,
    teamName: String,
    today: LocalDate,
): String? {
    val tournament = pickDefaultTournament(getTournaments(leagueId), today) ?: return null
    if (!isOngoing(tournament, today)) return null
    val ranking = findTeamRanking(getStandings(tournament.id), teamId, teamName) ?: return null
    return standingLine(leagueName, ranking)
}
