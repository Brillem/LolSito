package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.formatMatchResult

/** The winner of a match between two known teams, null while undecided or while a team is a placeholder. */
internal fun SectionMatch.decidedWinner(): Team? =
    if (hasBothRealTeams()) formatMatchResult(state, match.teams, match.format).winnerIndex?.let { match.teams[it] } else null

/** Standings teams carry an id; the code is the fallback when it is missing. */
internal val Team.identity: String get() = id ?: code

/** A "TBD" slot the API fills once the team is known (id "0", code "TBD"): never a participant or a winner. */
internal val Team.isPlaceholder: Boolean get() = id == PLACEHOLDER_TEAM_ID || code == PLACEHOLDER_TEAM_CODE

/** Both teams of the match are known. */
internal fun SectionMatch.hasBothRealTeams(): Boolean = match.teams.size >= 2 && match.teams.take(2).none { it.isPlaceholder }

private const val PLACEHOLDER_TEAM_ID = "0"
private const val PLACEHOLDER_TEAM_CODE = "TBD"
