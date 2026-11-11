package com.example.lolsito.core.polling

import kotlin.time.Duration.Companion.seconds

/** How often an in-progress series is reloaded on the match detail screen. */
val MATCH_DETAIL_POLL_INTERVAL = 30.seconds

/**
 * How often the stats of a live game are reloaded. The livestats feed rejects windows that ended less
 * than 20 seconds ago, but the window is requested [com.example.lolsito.feature.game.data.LATEST_WINDOW_DELAY]
 * behind now, so 20 seconds is safe and matches the feed's own frame cadence closely enough.
 */
val GAME_STATS_POLL_INTERVAL = 20.seconds

/** How often the "Live now" section of the schedule is refreshed. */
val LIVE_SECTION_POLL_INTERVAL = 30.seconds
