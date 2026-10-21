package com.example.lolsito.core.model

data class League(
    val id: String,
    val slug: String,
    val name: String,
    val region: String?,
    val imageUrl: String?,
)

enum class MatchState { UNSTARTED, IN_PROGRESS, COMPLETED, UNKNOWN }

enum class MatchOutcome { WIN, LOSS }

data class Team(
    val name: String,
    val code: String,
    val imageUrl: String?,
    val outcome: MatchOutcome?,
    val gameWins: Int?,
    /** Team id; only present in event details (the schedule does not send it). */
    val id: String? = null,
)

/** How a series is decided: [BEST_OF] ends once a team wins a majority, [PLAY_ALL] plays every game. */
enum class SeriesType { BEST_OF, PLAY_ALL, UNKNOWN }

data class SeriesFormat(val type: SeriesType, val count: Int) {
    /** "Bo3" for best-of, "2 games" for play-all, null when the type is unknown. */
    val label: String?
        get() = when {
            count <= 0 -> null
            type == SeriesType.BEST_OF -> "Bo$count"
            type == SeriesType.PLAY_ALL -> if (count == 1) "1 game" else "$count games"
            else -> null
        }

    /**
     * Whether the game wins alone prove the series is over. Best-of ends once a team reaches
     * count / 2 + 1; play-all only once every game has a winner; unknown types never end early.
     */
    fun isDecidedBy(gameWins: List<Int?>): Boolean = when {
        count <= 0 -> false
        type == SeriesType.BEST_OF -> gameWins.any { (it ?: 0) >= count / 2 + 1 }
        type == SeriesType.PLAY_ALL -> gameWins.sumOf { it ?: 0 } >= count
        else -> false
    }
}

data class Match(
    val id: String,
    val teams: List<Team>,
    /** Series format (e.g. best of 3), null when unknown. */
    val format: SeriesFormat?,
)
