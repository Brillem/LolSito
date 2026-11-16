package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.MatchState
import java.time.Duration
import java.time.Instant

private val COUNTDOWN_WINDOW: Duration = Duration.ofHours(24)

/**
 * Countdown to an unstarted match: "in 2h 15m" (minutes dropped when zero), "in 45m" under an hour,
 * "starting now" under a minute. Minutes round up, so it never reads "in 0m". Null when the match is not
 * [MatchState.UNSTARTED], already started, or more than 24 hours away.
 */
fun countdownLabel(startTime: Instant, now: Instant, state: MatchState): String? {
    if (state != MatchState.UNSTARTED) return null
    val remaining = Duration.between(now, startTime)
    if (remaining.isNegative || remaining > COUNTDOWN_WINDOW) return null
    if (remaining < Duration.ofMinutes(1)) return "starting now"
    val seconds = remaining.seconds + if (remaining.nano > 0) 1 else 0
    val totalMinutes = (seconds + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "in ${minutes}m"
        minutes == 0L -> "in ${hours}h"
        else -> "in ${hours}h ${minutes}m"
    }
}
