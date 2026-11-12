package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.pickDefaultTournament
import java.time.LocalDate

/** A team listed in the Teams tab; [id] opens its profile. */
data class TeamSummary(
    val id: String,
    val name: String,
    val code: String,
    val imageUrl: String?,
)

/** Roster roles in the order the profile shows them; [OTHER] holds any role that is not one of the five lanes. */
enum class PlayerRole(val label: String) {
    TOP("Top"),
    JUNGLE("Jungle"),
    MID("Mid"),
    BOTTOM("Bottom"),
    SUPPORT("Support"),
    OTHER("Other"),
}

/** A player of a team's roster. The API does not say who starts, so no player is marked as a starter. */
data class Player(
    val id: String?,
    val summonerName: String,
    val firstName: String?,
    val lastName: String?,
    val imageUrl: String?,
    val role: PlayerRole,
)

data class RoleGroup(val role: PlayerRole, val players: List<Player>)

data class TeamProfile(
    val id: String,
    val slug: String,
    val name: String,
    val code: String,
    val imageUrl: String?,
    val homeLeagueName: String?,
    val homeLeagueRegion: String?,
    /** Every player the API lists, grouped by role in [PlayerRole] order; roles without players are left out. */
    val roster: List<RoleGroup>,
)

interface TeamsRepository {
    /** [id] is the team id; null when the API knows no such team. */
    suspend fun getTeam(id: String): TeamProfile?
}

/**
 * The teams of a tournament's standings: every team in a ranking or in a section match, once each
 * (by id), sorted by name. Placeholders ("TBD") and teams without an id (nothing to open) are left out.
 */
fun teamsOfStandings(standings: Standings): List<TeamSummary> =
    standings.stages
        .flatMap { it.sections }
        .flatMap { section -> section.rankings.map { it.team } + section.matches.flatMap { it.match.teams } }
        .filterNot { it.isPlaceholder() }
        .mapNotNull { team -> team.id?.let { TeamSummary(it, team.name, team.code, team.imageUrl) } }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }

private fun Team.isPlaceholder(): Boolean = name.equals(TBD, ignoreCase = true) || code.equals(TBD, ignoreCase = true)

private const val TBD = "TBD"

/** The current teams of a league: those of the tournament [pickDefaultTournament] chooses for [today]. */
suspend fun StandingsRepository.currentTeams(leagueId: String, today: LocalDate): List<TeamSummary> {
    val tournament = pickDefaultTournament(getTournaments(leagueId), today) ?: return emptyList()
    return teamsOfStandings(getStandings(tournament.id))
}
