package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CountdownTest {

    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private fun label(after: Duration, state: MatchState = MatchState.UNSTARTED) =
        countdownLabel(now.plus(after), now, state)

    @Test
    fun `hours and minutes read as in 2h 15m`() {
        assertEquals("in 2h 15m", label(Duration.ofMinutes(135)))
    }

    @Test
    fun `whole hours drop the minutes`() {
        assertEquals("in 3h", label(Duration.ofHours(3)))
    }

    @Test
    fun `under an hour shows only minutes`() {
        assertEquals("in 45m", label(Duration.ofMinutes(45)))
    }

    @Test
    fun `minutes round up`() {
        assertEquals("in 15m", label(Duration.ofMinutes(14).plusSeconds(10)))
        assertEquals("in 1h", label(Duration.ofMinutes(59).plusSeconds(30)))
    }

    @Test
    fun `under a minute reads starting now`() {
        assertEquals("starting now", label(Duration.ofSeconds(30)))
        assertEquals("starting now", label(Duration.ZERO))
    }

    @Test
    fun `exactly 24 hours still counts down`() {
        assertEquals("in 24h", label(Duration.ofHours(24)))
    }

    @Test
    fun `beyond 24 hours has no countdown`() {
        assertNull(label(Duration.ofHours(24).plusSeconds(1)))
    }

    @Test
    fun `a start in the past has no countdown`() {
        assertNull(label(Duration.ofSeconds(-1)))
    }

    @Test
    fun `only unstarted matches get a countdown`() {
        listOf(MatchState.IN_PROGRESS, MatchState.COMPLETED, MatchState.UNKNOWN).forEach {
            assertNull(label(Duration.ofHours(1), it))
        }
    }
}
