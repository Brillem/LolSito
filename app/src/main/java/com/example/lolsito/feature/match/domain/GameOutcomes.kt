package com.example.lolsito.feature.match.domain

/** Tells which team won a finished game: its team id, or null when that cannot be told. */
fun interface GameWinnerSource {
    suspend fun winnerTeamId(gameId: String): String?
}

/**
 * How one game of a series ended: the index of the team that won it among the match's teams, and the
 * series score right after it (e.g. "1 - 1"), null when an earlier game's winner is unknown.
 */
data class GameOutcome(val winnerIndex: Int, val seriesScore: String?)

/** The ids of the completed games that have one, in game order: the games a winner can be asked for. */
fun MatchDetail.completedGameIds(): List<String> =
    games.filter { it.state == GameState.COMPLETED }.sortedBy { it.number }.mapNotNull { it.id }

/**
 * The outcome of each completed game by game id, from the [winnerTeamIds] the stats gave (by game id; a
 * missing or null entry is a game whose stats do not tell), completed with the series score: see
 * [completeGameWinners]. A game whose winner stays unknown has no entry.
 */
fun MatchDetail.gameOutcomes(winnerTeamIds: Map<String, String?>): Map<String, GameOutcome> {
    val completed = games.filter { it.state == GameState.COMPLETED }.sortedBy { it.number }
    val teamIds = teams.map { it.id }
    val known = completed.map { game ->
        val winnerId = game.id?.let(winnerTeamIds::get) ?: return@map null
        teamIds.indexOf(winnerId).takeIf { it >= 0 }
    }
    val winners = completeGameWinners(known, teams.map { it.gameWins })
    return completed.zip(gameOutcomes(winners))
        .mapNotNull { (game, outcome) -> if (game.id != null && outcome != null) game.id to outcome else null }
        .toMap()
}

/**
 * Fills in the winners the stats could not tell with what the series score leaves no doubt about.
 * [known] has one entry per completed game, in order: the index of its winner (0 or 1) or null.
 * [gameWins] is how many games each of the two teams has won.
 *
 * When every game still unknown must belong to one team for the score to add up (a sweep, or the last
 * open game of a series), they are given to it. When they could go either way they stay unknown. When
 * the known winners contradict the score, none of them can be trusted and all are dropped. Without the
 * wins of exactly two teams there is nothing to check against, and [known] is returned as is.
 */
fun completeGameWinners(known: List<Int?>, gameWins: List<Int?>): List<Int?> {
    if (gameWins.size != 2) return known
    val wins = gameWins.map { it ?: return known }
    val remaining = wins.mapIndexed { team, won -> won - known.count { it == team } }
    val unknown = known.count { it == null }
    if (remaining.any { it < 0 } || remaining.sum() != unknown) return known.map { null }
    val teamsStillMissingWins = remaining.count { it > 0 }
    if (unknown == 0 || teamsStillMissingWins != 1) return known
    val owner = remaining.indexOfFirst { it > 0 }
    return known.map { it ?: owner }
}

/**
 * The [GameOutcome] of each game from its winner, in order; null where the winner is unknown. The series
 * score is counted game by game, so it stops being shown after the first unknown winner.
 */
fun gameOutcomes(winners: List<Int?>): List<GameOutcome?> {
    val score = intArrayOf(0, 0)
    var scoreKnown = true
    return winners.map { winner ->
        if (winner == null || winner !in score.indices) {
            scoreKnown = false
            null
        } else {
            score[winner]++
            GameOutcome(winner, if (scoreKnown) "${score[0]} - ${score[1]}" else null)
        }
    }
}
