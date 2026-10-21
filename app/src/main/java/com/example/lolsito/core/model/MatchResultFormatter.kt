package com.example.lolsito.core.model

enum class TeamEmphasis { WINNER, LOSER, NEUTRAL }

/**
 * Display-ready result of a two-team match.
 * [score] is "a - b" only for completed matches with both game wins known.
 * [winnerIndex] points into the teams list (0 or 1), null when unknown or tied.
 */
data class MatchResultDisplay(
    val score: String?,
    val winnerIndex: Int?,
    val bestOf: String?,
) {
    fun emphasisFor(index: Int): TeamEmphasis = when (winnerIndex) {
        null -> TeamEmphasis.NEUTRAL
        index -> TeamEmphasis.WINNER
        else -> TeamEmphasis.LOSER
    }
}

/** Formats score, winner and best-of label. The outcome decides the winner; game wins are the fallback. */
fun formatMatchResult(state: MatchState, teams: List<Team>, bestOf: Int?): MatchResultDisplay {
    val bestOfLabel = bestOf?.takeIf { it > 0 }?.let { "Bo$it" }
    if (teams.size < 2 || state != MatchState.COMPLETED) {
        return MatchResultDisplay(score = null, winnerIndex = null, bestOf = bestOfLabel)
    }
    val (first, second) = teams
    val firstWins = first.gameWins
    val secondWins = second.gameWins
    val score = if (firstWins != null && secondWins != null) "$firstWins - $secondWins" else null
    return MatchResultDisplay(score, winnerFromOutcome(first, second) ?: winnerFromGameWins(firstWins, secondWins), bestOfLabel)
}

private fun winnerFromOutcome(first: Team, second: Team): Int? {
    val firstWon = first.outcome == MatchOutcome.WIN || second.outcome == MatchOutcome.LOSS
    val secondWon = second.outcome == MatchOutcome.WIN || first.outcome == MatchOutcome.LOSS
    return when {
        firstWon && !secondWon -> 0
        secondWon && !firstWon -> 1
        else -> null
    }
}

private fun winnerFromGameWins(first: Int?, second: Int?): Int? {
    if (first == null || second == null || first == second) return null
    return if (first > second) 0 else 1
}
