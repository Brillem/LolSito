package com.example.lolsito.feature.game.domain

/** A side of the map. */
enum class MapSide { BLUE, RED }

/**
 * The fewest towers a team must destroy to reach the enemy nexus: the three of one lane and the two
 * that guard the nexus.
 */
const val MIN_TOWERS_TO_WIN = 5

/**
 * The side that won a finished game, or null when the game is not finished or the stats do not tell.
 *
 * The feed never says who won, and has no figure for the nexus. What it has is what the nexus requires:
 * a team cannot destroy it without first taking an inhibitor and at least [MIN_TOWERS_TO_WIN] towers. So
 * when exactly one team did that, it won. When both did (a close game) or neither (a feed that stopped
 * early), the winner is unknown rather than guessed.
 */
fun GameStats.winnerSide(): MapSide? {
    if (!isFinished) return null
    val blue = blueTeam.couldTakeNexus()
    val red = redTeam.couldTakeNexus()
    return when {
        blue && !red -> MapSide.BLUE
        red && !blue -> MapSide.RED
        else -> null
    }
}

/** The id of the team on the [winnerSide], or null when the winner or its id is unknown. */
fun GameStats.winnerTeamId(): String? = when (winnerSide()) {
    MapSide.BLUE -> blueTeamId
    MapSide.RED -> redTeamId
    null -> null
}

private fun TeamStats.couldTakeNexus(): Boolean = inhibitors >= 1 && towers >= MIN_TOWERS_TO_WIN
