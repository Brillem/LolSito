package com.example.lolsito.feature.game.data

import java.time.Duration
import java.time.Instant

/** Game time between two gold samples. */
val GOLD_SAMPLE_INTERVAL: Duration = Duration.ofMinutes(2)

/**
 * startingTimes of the windows that sample a game's gold from its first frame at [start] up to
 * [until]: one every [interval] after [start], aligned with [alignToWindow], and only those that
 * start before [until] (the point at [until] comes from the stats themselves).
 */
fun goldSampleTimes(start: Instant, until: Instant, interval: Duration = GOLD_SAMPLE_INTERVAL): List<String> =
    generateSequence(1L) { it + 1 }
        .map { alignToWindow(start.plus(interval.multipliedBy(it))) }
        .takeWhile { it.isBefore(until) }
        .map(Instant::toString)
        .toList()
