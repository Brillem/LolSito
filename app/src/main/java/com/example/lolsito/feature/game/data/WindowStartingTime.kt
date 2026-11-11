package com.example.lolsito.feature.game.data

import java.time.Duration
import java.time.Instant

private const val WINDOW_SECONDS = 10L

/** How far behind [latestStartingTime] stays from now. */
val LATEST_WINDOW_DELAY: Duration = Duration.ofSeconds(60)

/** Floors [instant] to a 10-second boundary: the feed rejects any other startingTime with HTTP 400. */
fun alignToWindow(instant: Instant): Instant =
    Instant.ofEpochSecond(Math.floorDiv(instant.epochSecond, WINDOW_SECONDS) * WINDOW_SECONDS)

/**
 * startingTime (RFC 3339) whose window holds the latest frames of a game, live or completed.
 *
 * Observed on the feed: without startingTime it returns the first frames of the game; a window that
 * ends less than 20 seconds ago is rejected with HTTP 400; and any window after a completed game
 * ended returns its final frames (`gameState` "finished"), even weeks later. So one recent window
 * serves both cases: [now] minus [LATEST_WINDOW_DELAY], aligned down. The delay covers the 10-second
 * window plus those 20 seconds, with room left for a device clock that runs ahead.
 */
fun latestStartingTime(now: Instant): String = alignToWindow(now.minus(LATEST_WINDOW_DELAY)).toString()
