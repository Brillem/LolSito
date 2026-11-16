package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.decidedWinner
import com.example.lolsito.feature.standings.domain.isPlaceholder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/** Result label of a tournament being played. */
const val RESULT_IN_PROGRESS = "In progress"

/** Result label of the winner of the final. */
const val RESULT_CHAMPION = "Champion"

/** Result label of the loser of the final. */
const val RESULT_FINALIST = "Finalist"

/** Tournaments looked at in the team's home league, and in each international league. */
internal const val HOME_TOURNAMENTS = 3
internal const val INTERNATIONAL_TOURNAMENTS = 2

/** Rows of "Recent tournaments". */
const val MAX_RECENT_TOURNAMENTS = 5

/** A tournament the team took part in, with how far it got ([result] is null when none is known or shown). */
data class TeamTournament(val leagueId: String, val tournament: Tournament, val result: String?)

/**
 * The [count] most recent tournaments of [tournaments] that have started by [today] (start date not after it),
 * newest start first. A tournament without a start date is not known to have started.
 */
fun startedTournaments(tournaments: List<Tournament>, today: LocalDate, count: Int): List<Tournament> =
    tournaments
        .filter { t -> t.startDate?.let { !it.isAfter(today) } == true }
        .sortedByDescending { it.startDate }
        .take(count)

/**
 * Matches the team inside one [Standings]: by [teamId] when any team of the standings has that id, otherwise
 * by [teamName] (trimmed, case-insensitive) - the same rule as [findTeamRanking]. A "TBD" slot is never the team.
 */
private class TeamMatcher(standings: Standings, private val teamId: String, teamName: String) {
    private val name = teamName.trim()
    private val teams: List<Team> = standings.stages.flatMap { stage ->
        stage.sections.flatMap { section ->
            section.rankings.map { it.team } + section.matches.flatMap { it.match.teams }
        }
    }
    private val byId = teams.any { it.id == teamId }

    val isPresent: Boolean = teams.any(::matches)

    fun matches(team: Team): Boolean =
        if (byId) team.id == teamId else !team.isPlaceholder && team.name.trim().equals(name, ignoreCase = true)

    fun plays(match: SectionMatch): Boolean = match.match.teams.any(::matches)
}

/** Whether the team is in the rankings or in any match of [standings]. */
fun participated(standings: Standings, teamId: String, teamName: String): Boolean =
    TeamMatcher(standings, teamId, teamName).isPresent

/**
 * How far the team got in [tournament], from its [standings] and as of [today]; null when it cannot be said.
 *
 * 1. A tournament being played ([isOngoing]) is [RESULT_IN_PROGRESS].
 * 2. When the last stage is an elimination bracket ending in a final that is decided and played by the team:
 *    [RESULT_CHAMPION] for the winner, [RESULT_FINALIST] for the loser. A final not played yet, or without a
 *    known winner, claims nothing and the next rule applies.
 * 3. Otherwise the last stage the team appears in decides. A ranked table gives the place ("3rd", the stage name
 *    when the API sent no ordinal). A bracket or Swiss stage that is not the last one gives its name ("Play-Ins",
 *    "Swiss"); in the last stage it is the round of the team's last match ("Semifinals"). When that round is only a
 *    number ("Round 3") an elimination stage gives no label (null: a qualifier ladder's name, "Road to MSI",
 *    does not read as a result), any other stage gives its name.
 */
fun tournamentResult(
    standings: Standings,
    teamId: String,
    teamName: String,
    tournament: Tournament,
    today: LocalDate,
): String? {
    if (isOngoing(tournament, today)) return RESULT_IN_PROGRESS
    val matcher = TeamMatcher(standings, teamId, teamName)
    val stages = standings.stages.filter { it.sections.isNotEmpty() }
    val lastStage = stages.lastOrNull() ?: return null
    finalResult(lastStage, matcher)?.let { return it }
    for (stage in stages.asReversed()) {
        val ranking = stage.sections.flatMap { it.rankings }.firstOrNull { matcher.matches(it.team) }
        if (ranking != null) return ranking.ordinal?.let { ordinalLabel(it) } ?: stage.name.takeIf { it.isNotBlank() }
        val round = lastRound(stage, matcher) ?: continue
        val stageName = stage.name.takeIf { it.isNotBlank() }
        if (stage !== lastStage) return stageName ?: round.section.takeIf { it.isNotBlank() }
        round.cell.takeUnless { it.isBlank() || BARE_ROUND.matches(it.trim()) }?.let { return it }
        if (stage.isElimination) return null
        return stageName
            ?: round.section.takeIf { it.isNotBlank() }
    }
    return null
}

/** "Round 3": a round that is only a number says nothing about how far the team got. */
private val BARE_ROUND = Regex("round\\s*\\d+", RegexOption.IGNORE_CASE)

/** A cell named after the final: "Finals", "Grand Final"; "Semifinals" and "Quarterfinals" are not. */
private val FINAL_CELL = Regex("(^|\\W)finals?$", RegexOption.IGNORE_CASE)

/**
 * [RESULT_CHAMPION] or [RESULT_FINALIST] when [stage] is an elimination bracket whose final - the only match of
 * the last cell of its last column, named like a final and feeding no other match - is decided and played by the
 * team; null otherwise. A double elimination ends the same way: both bracket finals feed the grand final.
 */
private fun finalResult(stage: StandingsStage, matcher: TeamMatcher): String? {
    if (!stage.isElimination) return null
    val section = stage.sections.lastOrNull { it.columns.isNotEmpty() } ?: return null
    val cell = section.columns.lastOrNull { it.cells.isNotEmpty() }?.cells?.last() ?: return null
    if (!FINAL_CELL.containsMatchIn(cell.name.trim())) return null
    val final = cell.matches.singleOrNull() ?: return null
    if (section.winnerLinks.any { it.fromMatchId == final.match.id }) return null
    if (!matcher.plays(final)) return null
    val winner = final.decidedWinner() ?: return null
    return if (matcher.matches(winner)) RESULT_CHAMPION else RESULT_FINALIST
}

/** Where the team last played in a stage: the name of the section and of the round (cell) in it. */
private class LastRound(val section: String, val cell: String)

/** The last cell (last section, last column, last cell) of [stage] with a match of the team; null when it has none. */
private fun lastRound(stage: StandingsStage, matcher: TeamMatcher): LastRound? {
    for (section in stage.sections.asReversed()) {
        for (column in section.columns.asReversed()) {
            for (cell in column.cells.asReversed()) {
                if (cell.matches.any(matcher::plays)) return LastRound(section.name, cell.name)
            }
        }
        if (section.columns.isEmpty() && section.matches.any(matcher::plays)) return LastRound(section.name, "")
    }
    return null
}

/**
 * The tournaments the team recently took part in, most recent start first, at most [MAX_RECENT_TOURNAMENTS].
 * The candidates are the [HOME_TOURNAMENTS] latest started ones of [homeLeagueId] and the
 * [INTERNATIONAL_TOURNAMENTS] latest of each international league ([InternationalLeagues]); a candidate counts
 * when the team is in its standings. Every request runs concurrently and a failing one only drops its own
 * candidates, so the list can come back empty. With [hideResults] (the no-spoilers mode) only a tournament
 * in progress keeps its result.
 */
suspend fun StandingsRepository.recentTeamTournaments(
    homeLeagueId: String?,
    teamId: String,
    teamName: String,
    today: LocalDate,
    hideResults: Boolean,
): List<TeamTournament> = coroutineScope {
    val leagueIds = (listOfNotNull(homeLeagueId) + InternationalLeagues.ids).distinct()
    val candidates = leagueIds.map { leagueId ->
        async {
            val count = if (leagueId == homeLeagueId) HOME_TOURNAMENTS else INTERNATIONAL_TOURNAMENTS
            val tournaments = swallowFailure { getTournaments(leagueId) } ?: return@async emptyList()
            startedTournaments(tournaments, today, count).map { leagueId to it }
        }
    }.awaitAll().flatten()
    candidates.map { (leagueId, tournament) ->
        async {
            val standings = swallowFailure { getStandings(tournament.id) } ?: return@async null
            if (!participated(standings, teamId, teamName)) return@async null
            val result = tournamentResult(standings, teamId, teamName, tournament, today)
                ?.takeUnless { hideResults && it != RESULT_IN_PROGRESS }
            TeamTournament(leagueId, tournament, result)
        }
    }.awaitAll().filterNotNull()
        .distinctBy { it.tournament.id }
        .sortedByDescending { it.tournament.startDate }
        .take(MAX_RECENT_TOURNAMENTS)
}

/** [block]'s value, or null when it fails; cancellation still propagates. */
private suspend fun <T> swallowFailure(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
