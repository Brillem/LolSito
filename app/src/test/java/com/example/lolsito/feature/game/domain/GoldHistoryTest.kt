package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GoldHistoryTest {

    private val start = Instant.parse("2026-10-15T23:11:17.655Z")

    @Test
    fun `of turns samples into blue minus red gold by game time since the start, in time order`() {
        val history = GoldHistory.of(
            start,
            listOf(
                GoldSample(start.plusSeconds(240), blueGold = 9000, redGold = 9500),
                GoldSample(start, blueGold = 0, redGold = 0),
                GoldSample(start.plusSeconds(120), blueGold = 5000, redGold = 4900),
            ),
        )

        assertEquals(start, history.start)
        assertEquals(
            listOf(
                GoldPoint(Duration.ZERO, 0),
                GoldPoint(Duration.ofSeconds(120), 100),
                GoldPoint(Duration.ofSeconds(240), -500),
            ),
            history.points,
        )
    }

    @Test
    fun `of keeps one point per frame time and drops samples from before the start`() {
        val history = GoldHistory.of(
            start,
            listOf(
                GoldSample(start.minusSeconds(5), blueGold = 100, redGold = 0),
                GoldSample(start.plusSeconds(120), blueGold = 5000, redGold = 4900),
                GoldSample(start.plusSeconds(120), blueGold = 5000, redGold = 4900),
            ),
        )

        assertEquals(listOf(GoldPoint(Duration.ofSeconds(120), 100)), history.points)
    }

    @Test
    fun `including appends the gold difference of stats recorded after the last point`() {
        val history = GoldHistory.of(start, listOf(GoldSample(start, 0, 0)))

        val grown = history.including(stats(frameTime = start.plusSeconds(300), blueGold = 12000, redGold = 11000))

        assertEquals(start, grown.start)
        assertEquals(listOf(GoldPoint(Duration.ZERO, 0), GoldPoint(Duration.ofSeconds(300), 1000)), grown.points)
    }

    @Test
    fun `including ignores stats that are not after the last point or have no frame time`() {
        val history = GoldHistory.of(start, listOf(GoldSample(start, 0, 0), GoldSample(start.plusSeconds(120), 10, 0)))

        assertSame(history, history.including(stats(frameTime = start.plusSeconds(120), blueGold = 1, redGold = 0)))
        assertSame(history, history.including(stats(frameTime = start.plusSeconds(60), blueGold = 1, redGold = 0)))
        assertSame(history, history.including(stats(frameTime = null, blueGold = 1, redGold = 0)))
    }

    private fun stats(frameTime: Instant?, blueGold: Int, redGold: Int) = GameStats(
        isFinished = false,
        blueTeam = TeamStats(blueGold, 0, 0, 0, 0, emptyList()),
        redTeam = TeamStats(redGold, 0, 0, 0, 0, emptyList()),
        frameTime = frameTime,
    )
}
