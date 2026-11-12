package com.example.lolsito.feature.game.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GoldSamplingTest {

    private val start = Instant.parse("2026-10-15T23:11:17.655Z")

    @Test
    fun `one aligned window every 2 minutes after the start, up to the end`() {
        val times = goldSampleTimes(start, until = Instant.parse("2026-10-15T23:20:00Z"))

        assertEquals(
            listOf("2026-10-15T23:13:10Z", "2026-10-15T23:15:10Z", "2026-10-15T23:17:10Z", "2026-10-15T23:19:10Z"),
            times,
        )
    }

    @Test
    fun `a window that would start at or after the end is not requested`() {
        val times = goldSampleTimes(start, until = Instant.parse("2026-10-15T23:15:10Z"))

        assertEquals(listOf("2026-10-15T23:13:10Z"), times)
    }

    @Test
    fun `nothing is requested before 2 minutes have passed or when the end is not after the start`() {
        assertEquals(emptyList<String>(), goldSampleTimes(start, until = start.plusSeconds(100)))
        assertEquals(emptyList<String>(), goldSampleTimes(start, until = start))
        assertEquals(emptyList<String>(), goldSampleTimes(start, until = start.minusSeconds(60)))
    }

    @Test
    fun `a 35 minute game takes 17 requests`() {
        val times = goldSampleTimes(start, until = Instant.parse("2026-10-15T23:46:45.601Z"))

        assertEquals(17, times.size)
        assertEquals("2026-10-15T23:45:10Z", times.last())
        assertEquals(Duration.ofMinutes(2), GOLD_SAMPLE_INTERVAL)
    }
}
