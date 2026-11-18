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

    /** A [TeamSearchIndex] over [getAllTeams]; an implementation that keeps the list should keep this too. */
    suspend fun getSearchIndex(): TeamSearchIndex = TeamSearchIndex(getAllTeams())
}

/** The shortest query (after trimming) that searches anything. */
const val MIN_SEARCH_LENGTH = 2

/** The most teams, and the most players, a search returns. */
const val SEARCH_RESULT_LIMIT = 20

/**
 * The fewest players a team needs to be searched: a team fields five. The API still marks defunct teams as
 * active but keeps an empty or one-player roster for them, so a smaller roster means a stale team.
 */
const val MIN_SEARCHABLE_ROSTER = 5

/**
 * Teams and players of [teams] matching [query], ignoring case and diacritics. Only active teams with a
 * home league, an id and at least [MIN_SEARCHABLE_ROSTER] players are searched; a query shorter than
 * [MIN_SEARCH_LENGTH] after trimming matches nothing.
 *
 * Teams match on name or code: an exact code first, then a name or code that starts with the query, then
 * one that contains it; ties by name. Players match on summoner name or "first last" name: a summoner name
 * that starts with the query first, then one that contains it, then a match on the real name; ties by
 * summoner name. At most [limit] of each.
 *
 * A player with an id that is on several searchable teams (a main team and its academy) is listed once, under
 * the team whose home league comes first in [leagueOrder] (league names, matched trimmed and ignoring case; a
 * league not in it ranks last); ties keep the first team of [teams]. Players without an id are never merged.
 *
 * To search the same teams again and again, build a [TeamSearchIndex] once and call [TeamSearchIndex.search].
 */
fun searchTeamsAndPlayers(
    teams: List<DirectoryTeam>,
    query: String,
    limit: Int = SEARCH_RESULT_LIMIT,
    leagueOrder: List<String> = emptyList(),
): TeamSearchResults = TeamSearchIndex(teams).search(query, limit, leagueOrder)

/**
 * [teams] ready to be searched by the rules of [searchTeamsAndPlayers]: the searchable teams are picked and
 * every name, code and league normalized once, when the index is built, so a query only compares strings. The
 * players merged by league order are kept for the last order asked. Safe to use from several threads.
 */
class TeamSearchIndex(teams: List<DirectoryTeam>) {

    private class IndexedTeam(val team: DirectoryTeam, val name: String, val code: String, val league: String?)

    private class IndexedPlayer(val hit: PlayerHit, val owner: IndexedTeam, val summoner: String, val realName: String)

    private class MergedPlayers(val leagueOrder: List<String>, val players: List<IndexedPlayer>)

    private val teams: List<IndexedTeam> = teams
        .filter { it.active && it.homeLeagueName != null && it.players.size >= MIN_SEARCHABLE_ROSTER }
        .map { IndexedTeam(it, normalizeForSearch(it.name), normalizeForSearch(it.code), it.homeLeagueName?.let(::normalizeForSearch)) }

    private val players: List<IndexedPlayer> = this.teams.flatMap { team ->
        team.team.players.map { player ->
            IndexedPlayer(
                PlayerHit(player, team.team),
                team,
                normalizeForSearch(player.summonerName),
                normalizeForSearch(listOfNotNull(player.firstName, player.lastName).joinToString(" ")),
            )
        }
    }

    @Volatile
    private var merged: MergedPlayers? = null

    fun search(query: String, limit: Int = SEARCH_RESULT_LIMIT, leagueOrder: List<String> = emptyList()): TeamSearchResults {
        val needle = normalizeForSearch(query)
        if (needle.length < MIN_SEARCH_LENGTH) return TeamSearchResults()

        val teamHits = teams
            .mapNotNull { team -> team.rank(needle)?.let { rank -> Triple(rank, team.name, team.team) } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { it.third }

        val playerHits = mergedPlayers(leagueOrder)
            .mapNotNull { player -> player.rank(needle)?.let { rank -> Triple(rank, player.summoner, player.hit) } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { it.third }

        return TeamSearchResults(teamHits, playerHits)
    }

    private fun mergedPlayers(leagueOrder: List<String>): List<IndexedPlayer> {
        merged?.takeIf { it.leagueOrder == leagueOrder }?.let { return it.players }
        val leagueRanks = leagueOrder.withIndex().reversed().associate { (i, name) -> normalizeForSearch(name) to i }
        val kept = players.keepBestTeamPerPlayer { team -> team.league?.let { leagueRanks[it] } ?: Int.MAX_VALUE }
        merged = MergedPlayers(leagueOrder.toList(), kept)
        return kept
    }

    /** One entry per player id, on the team [rankOf] puts first (earlier in the list wins a tie); the rest keep their order. */
    private fun List<IndexedPlayer>.keepBestTeamPerPlayer(rankOf: (IndexedTeam) -> Int): List<IndexedPlayer> {
        val best = HashMap<String, IndexedPlayer>()
        for (entry in this) {
            val id = entry.hit.player.id?.takeIf { it.isNotBlank() } ?: continue
            val kept = best[id]
            if (kept == null || rankOf(entry.owner) < rankOf(kept.owner)) best[id] = entry
        }
        return filter { entry ->
            val id = entry.hit.player.id?.takeIf { it.isNotBlank() } ?: return@filter true
            best[id] === entry
        }
    }

    private fun IndexedTeam.rank(needle: String): Int? = when {
        code == needle -> 0
        name.startsWith(needle) || code.startsWith(needle) -> 1
        name.contains(needle) || code.contains(needle) -> 2
        else -> null
    }

    private fun IndexedPlayer.rank(needle: String): Int? {
        if (summoner.isEmpty()) return null
        return when {
            summoner.startsWith(needle) -> 0
            summoner.contains(needle) -> 1
            realName.contains(needle) -> 2
            else -> null
        }
    }
}

/** Lowercase, trimmed, without accents: "Rékkles" and "  rekkles " compare equal. */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

private val COMBINING_MARKS = Regex("\\p{M}+")
