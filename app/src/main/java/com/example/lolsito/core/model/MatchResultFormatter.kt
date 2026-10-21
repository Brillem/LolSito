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
    val formatLabel: String?,
) {
    fun emphasisFor(index: Int): TeamEmphasis = when (winnerIndex) {
        null -> TeamEmphasis.NEUTRAL
        index -> TeamEmphasis.WINNER
        else -> TeamEmphasis.LOSER
    }
}

/**
 * Formats score, winner and series format label. The outcome decides the winner; game wins are the
 * fallback. A play-all series without outcomes is only shown as decided once every game has a winner.
 */
fun formatMatchResult(state: MatchState, teams: List<Team>, format: SeriesFormat?): MatchResultDisplay {
    val formatLabel = format?.label
    if (teams.size < 2 || state != MatchState.COMPLETED || isUnfinishedPlayAll(teams, format)) {
        return MatchResultDisplay(score = null, winnerIndex = null, formatLabel = formatLabel)
    }
    val (first, second) = teams
    val firstWins = first.gameWins
    val secondWins = second.gameWins
    val score = if (firstWins != null && secondWins != null) "$firstWins - $secondWins" else null
    return MatchResultDisplay(score, winnerFromOutcome(first, second) ?: winnerFromGameWins(firstWins, secondWins), formatLabel)
}

private fun isUnfinishedPlayAll(teams: List<Team>, format: SeriesFormat?): Boolean =
    format?.type == SeriesType.PLAY_ALL &&
        teams.none { it.outcome != null } &&
        !format.isDecidedBy(teams.map { it.gameWins })

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
