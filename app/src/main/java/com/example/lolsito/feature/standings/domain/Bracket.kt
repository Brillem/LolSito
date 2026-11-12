package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.formatMatchResult

/** One column of a single-elimination bracket, e.g. "Quarterfinals" with its 4 matches. */
data class BracketRound(val name: String, val matches: List<SectionMatch>, val linkedToNext: Boolean = true)

/**
 * A section as a single-elimination bracket, or null when it does not look like one.
 *
 * The API sends no links between matches (`previousMatchIds` is always empty), so the shape comes
 * from the matches themselves: a match-only section with 3, 7 or 15 matches in round order, where
 * matches 2k and 2k+1 of a round feed match k of the next. The bracket is only built when the
 * winner of every decided match appears in the match it feeds (in either slot); anything else,
 * such as Play-Ins, Swiss or double elimination, stays a list.
 */
fun buildBracket(section: StandingsSection): List<BracketRound>? {
    if (!section.isMatchOnly) return null
    val firstRoundSize = FIRST_ROUND_SIZES[section.matches.size] ?: return null
    val rounds = mutableListOf<List<SectionMatch>>()
    var start = 0
    var size = firstRoundSize
    while (size >= 1) {
        rounds += section.matches.subList(start, start + size)
        start += size
        size /= 2
    }
    val winnersAdvance = rounds.zipWithNext().all { (round, next) ->
        round.withIndex().all { (index, match) ->
            val winner = match.winner() ?: return@all true
            next[index / 2].match.teams.any { it.identity == winner.identity }
        }
    }
    if (!winnersAdvance) return null
    return rounds.map { BracketRound(roundName(it.size), it) }
}

/** Total matches of a bracket mapped to the size of its first round. */
private val FIRST_ROUND_SIZES = mapOf(3 to 2, 7 to 4, 15 to 8)

private fun roundName(matches: Int): String = when (matches) {
    8 -> "Round of 16"
    4 -> "Quarterfinals"
    2 -> "Semifinals"
    else -> "Final"
}

/** The winner as the match result shows it, null while undecided. */
private fun SectionMatch.winner(): Team? =
    formatMatchResult(state, match.teams, match.format).winnerIndex?.let { match.teams[it] }

/** The winner of a match between two known teams, null while undecided or while a team is a placeholder. */
internal fun SectionMatch.decidedWinner(): Team? = if (hasBothRealTeams()) winner() else null

/** The other team of a decided match between two known teams. */
internal fun SectionMatch.decidedLoser(): Team? = decidedWinner()?.let { w -> match.teams.take(2).first { it.identity != w.identity } }

/** Standings teams carry an id; the code is the fallback when it is missing. */
internal val Team.identity: String get() = id ?: code

/** A "TBD" slot the API fills once the team is known (id "0", code "TBD"): never a participant or a winner. */
internal val Team.isPlaceholder: Boolean get() = id == PLACEHOLDER_TEAM_ID || code == PLACEHOLDER_TEAM_CODE

/** Both teams of the match are known. */
internal fun SectionMatch.hasBothRealTeams(): Boolean = match.teams.size >= 2 && match.teams.take(2).none { it.isPlaceholder }

/** The teams of the match that are known, without placeholders. */
internal val SectionMatch.realTeams: List<Team> get() = match.teams.take(2).filterNot { it.isPlaceholder }

private const val PLACEHOLDER_TEAM_ID = "0"
private const val PLACEHOLDER_TEAM_CODE = "TBD"
