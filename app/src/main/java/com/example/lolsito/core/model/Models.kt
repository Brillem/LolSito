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
)

data class Match(
    val id: String,
    val teams: List<Team>,
    /** Best-of count (e.g. 1, 3, 5), null when unknown. */
    val bestOf: Int?,
)
