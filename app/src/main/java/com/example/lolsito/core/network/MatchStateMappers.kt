package com.example.lolsito.core.network

import com.example.lolsito.core.model.MatchState

/** Maps the API match state; anything unrecognized (or missing) is [MatchState.UNKNOWN]. */
fun String?.toMatchState(): MatchState = when (this) {
    "unstarted" -> MatchState.UNSTARTED
    "inProgress" -> MatchState.IN_PROGRESS
    "completed" -> MatchState.COMPLETED
    else -> MatchState.UNKNOWN
}
