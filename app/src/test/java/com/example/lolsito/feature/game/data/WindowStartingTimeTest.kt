package com.example.lolsito.feature.game.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WindowStartingTimeTest {

    @Test
    fun `an instant already on a 10 second boundary is kept`() {
        assertEquals(Instant.parse("2026-10-15T23:30:00Z"), alignToWindow(Instant.parse("2026-10-15T23:30:00Z")))
    }

    @Test
    fun `seconds are floored to the previous 10 second boundary`() {
        assertEquals(Instant.parse("2026-10-15T23:30:00Z"), alignToWindow(Instant.parse("2026-10-15T23:30:05Z")))
        assertEquals(Instant.parse("2026-10-15T23:30:50Z"), alignToWindow(Instant.parse("2026-10-15T23:30:59Z")))
    }

    @Test
    fun `fractions of a second are dropped`() {
        assertEquals(Instant.parse("2026-10-15T23:30:00Z"), alignToWindow(Instant.parse("2026-10-15T23:30:09.999Z")))
        assertEquals(Instant.parse("2026-10-15T23:30:10Z"), alignToWindow(Instant.parse("2026-10-15T23:30:10.001Z")))
    }

    @Test
    fun `latest starting time is one minute back, aligned and without fraction`() {
        assertEquals("2026-11-11T16:10:10Z", latestStartingTime(Instant.parse("2026-11-11T16:11:15.900Z")))
    }

    @Test
    fun `latest starting time crosses hour and day boundaries`() {
        assertEquals("2026-12-31T23:59:00Z", latestStartingTime(Instant.parse("2027-01-01T00:00:00Z")))
    }
}
