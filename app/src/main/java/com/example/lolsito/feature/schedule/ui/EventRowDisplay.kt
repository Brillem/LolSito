package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.liveScoreLabel

/** What an [EventRow] shows of a match's outcome: the formatted [result] and the [score] text for its center, if any. */
data class EventRowDisplay(val result: MatchResultDisplay, val score: String?)

/**
 * With [noSpoilers] on, a finished match reads "vs" and has no winner, so no team is dimmed; the series
 * format stays. A live match keeps its current score (it is not a finished result) and every other state is untouched.
 */
fun eventRowDisplay(event: ScheduleEvent, isLive: Boolean, noSpoilers: Boolean): EventRowDisplay {
    val teams = event.match?.teams.orEmpty()
    val formatted = formatMatchResult(event.state, teams, event.match?.format)
    val hidden = noSpoilers && !isLive && event.state == MatchState.COMPLETED
    val result = if (hidden) formatted.copy(score = null, winnerIndex = null) else formatted
    val score = when {
        isLive -> liveScoreLabel(teams) ?: "vs"
        hidden -> "vs"
        else -> result.score
    }
    return EventRowDisplay(result, score)
}
