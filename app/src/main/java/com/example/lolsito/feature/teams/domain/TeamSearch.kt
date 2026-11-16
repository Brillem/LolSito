package com.example.lolsito.feature.teams.domain

import java.text.Normalizer

/** One team of the full team list the search runs over; [id] opens its profile. */
data class DirectoryTeam(
    val id: String,
    val name: String,
    val code: String,
    val imageUrl: String?,
    /** The name of the league the team belongs to, or null for a team with none. */
    val homeLeagueName: String?,
    /** Whether the API says the team is still active. */
    val active: Boolean,
    val players: List<Player>,
)

/** A player that matched the search, with the team that opens when it is tapped. */
data class PlayerHit(val player: Player, val team: DirectoryTeam)

data class TeamSearchResults(
    val teams: List<DirectoryTeam> = emptyList(),
    val players: List<PlayerHit> = emptyList(),
) {
    val isEmpty: Boolean get() = teams.isEmpty() && players.isEmpty()
}

/** Every team the API lists, fetched on first use and kept for the next queries. */
interface TeamDirectoryRepository {
    suspend fun getAllTeams(): List<DirectoryTeam>
}

/** The shortest query (after trimming) that searches anything. */
const val MIN_SEARCH_LENGTH = 2

/** The most teams, and the most players, a search returns. */
const val SEARCH_RESULT_LIMIT = 20

/**
 * Teams and players of [teams] matching [query], ignoring case and diacritics. Only active teams with a
 * home league are searched; a query shorter than [MIN_SEARCH_LENGTH] after trimming matches nothing.
 *
 * Teams match on name or code: an exact code first, then a name or code that starts with the query, then
 * one that contains it; ties by name. Players match on summoner name or "first last" name: a summoner name
 * that starts with the query first, then one that contains it, then a match on the real name; ties by
 * summoner name. At most [limit] of each.
 */
fun searchTeamsAndPlayers(teams: List<DirectoryTeam>, query: String, limit: Int = SEARCH_RESULT_LIMIT): TeamSearchResults {
    val needle = normalizeForSearch(query)
    if (needle.length < MIN_SEARCH_LENGTH) return TeamSearchResults()
    val searchable = teams.filter { it.active && it.homeLeagueName != null }

    val teamHits = searchable
        .mapNotNull { team -> team.rank(needle)?.let { rank -> Triple(rank, normalizeForSearch(team.name), team) } }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(limit)
        .map { it.third }

    val playerHits = searchable
        .flatMap { team -> team.players.map { player -> PlayerHit(player, team) } }
        .mapNotNull { hit -> hit.player.rank(needle)?.let { rank -> Triple(rank, normalizeForSearch(hit.player.summonerName), hit) } }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(limit)
        .map { it.third }

    return TeamSearchResults(teamHits, playerHits)
}

private fun DirectoryTeam.rank(needle: String): Int? {
    val name = normalizeForSearch(name)
    val code = normalizeForSearch(code)
    return when {
        code == needle -> 0
        name.startsWith(needle) || code.startsWith(needle) -> 1
        name.contains(needle) || code.contains(needle) -> 2
        else -> null
    }
}

private fun Player.rank(needle: String): Int? {
    val summoner = normalizeForSearch(summonerName)
    if (summoner.isEmpty()) return null
    val realName = normalizeForSearch(listOfNotNull(firstName, lastName).joinToString(" "))
    return when {
        summoner.startsWith(needle) -> 0
        summoner.contains(needle) -> 1
        realName.contains(needle) -> 2
        else -> null
    }
}

/** Lowercase, trimmed, without accents: "Rékkles" and "  rekkles " compare equal. */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

private val COMBINING_MARKS = Regex("\\p{M}+")
