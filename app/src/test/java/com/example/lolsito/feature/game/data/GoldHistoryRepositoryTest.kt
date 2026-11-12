package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.core.network.dto.LiveStatsDetailsResponse
import com.example.lolsito.core.network.dto.LiveStatsFrameDto
import com.example.lolsito.core.network.dto.LiveStatsTeamFrameDto
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.GoldPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class GoldHistoryRepositoryTest {

    private val start = Instant.parse("2026-10-15T23:11:17.655Z")
    private val end = Instant.parse("2026-10-15T23:20:00Z")
    private val api = WindowApi()
    private val repository = GameStatsRepositoryImpl(api, Clock.fixed(end, ZoneOffset.UTC))

    @Test
    fun `asks for the first frames, then one window every 2 minutes up to the end, and maps the gold difference`() = runTest {
        val history = repository.getGoldHistory("g1", until = end)!!

        assertEquals(listOf(FIRST_FRAMES, "2026-10-15T23:13:10Z", "2026-10-15T23:15:10Z", "2026-10-15T23:17:10Z", "2026-10-15T23:19:10Z"), api.requested)
        assertEquals(start, history.start)
        assertEquals(
            listOf(
                GoldPoint(Duration.ZERO, 0),
                GoldPoint(Duration.between(start, Instant.parse("2026-10-15T23:13:19Z")), 1000),
                GoldPoint(Duration.between(start, Instant.parse("2026-10-15T23:15:19Z")), 2000),
                GoldPoint(Duration.between(start, Instant.parse("2026-10-15T23:17:19Z")), 3000),
                GoldPoint(Duration.between(start, Instant.parse("2026-10-15T23:19:19Z")), 4000),
            ),
            history.points,
        )
    }

    @Test
    fun `no more than 3 windows are requested at once`() = runTest {
        api.delayMillis = 100

        repository.getGoldHistory("g1", until = Instant.parse("2026-10-15T23:46:45Z"))

        assertEquals(18, api.requested.size)
        assertEquals(3, api.maxInFlight)
    }

    @Test
    fun `windows already fetched for a game are not requested again`() = runTest {
        repository.getGoldHistory("g1", until = end)
        api.requested.clear()

        val later = repository.getGoldHistory("g1", until = Instant.parse("2026-10-15T23:24:00Z"))!!

        assertEquals(listOf("2026-10-15T23:21:10Z", "2026-10-15T23:23:10Z"), api.requested)
        assertEquals(7, later.points.size)
    }

    @Test
    fun `the cache is per game`() = runTest {
        repository.getGoldHistory("g1", until = end)
        api.requested.clear()

        repository.getGoldHistory("g2", until = end)

        assertEquals(5, api.requested.size)
        assertEquals(FIRST_FRAMES, api.requested.first())
    }

    @Test
    fun `a window that fails or has no frames is left out and asked for again next time`() = runTest {
        api.failing += "2026-10-15T23:15:10Z"
        api.empty += "2026-10-15T23:17:10Z"

        val history = repository.getGoldHistory("g1", until = end)!!

        assertEquals(listOf(0, 1000, 4000), history.points.map { it.difference })
        api.requested.clear()
        api.failing.clear()
        api.empty.clear()

        val retried = repository.getGoldHistory("g1", until = end)!!

        assertEquals(listOf("2026-10-15T23:15:10Z", "2026-10-15T23:17:10Z"), api.requested)
        assertEquals(listOf(0, 1000, 2000, 3000, 4000), retried.points.map { it.difference })
    }

    @Test
    fun `no history when the first frames cannot be had`() = runTest {
        api.empty += FIRST_FRAMES
        assertNull(repository.getGoldHistory("g1", until = end))

        api.empty.clear()
        api.failing += FIRST_FRAMES
        assertNull(repository.getGoldHistory("g1", until = end))
        assertEquals(listOf(FIRST_FRAMES, FIRST_FRAMES), api.requested)
    }

    /**
     * Answers the first frames (empty startingTime) at [start] with no gold difference, and any other
     * window with frames 9 seconds into it where blue leads by 1000 per 2 minutes since 23:11:10.
     */
    private inner class WindowApi : LiveStatsApi {
        val requested = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        val empty = mutableSetOf<String>()
        var delayMillis = 0L
        var maxInFlight = 0
            private set
        private var inFlight = 0

        override suspend fun getWindow(gameId: String, startingTime: String): Response<LiveStatsWindowResponse> {
            requested += startingTime
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                delay(delayMillis)
                if (startingTime in failing) throw IOException("boom")
                if (startingTime in empty) return Response.success<LiveStatsWindowResponse>(204, null)
                if (startingTime.isEmpty()) {
                    return Response.success(window(frame(start, 0, 0), frame(start.plusMillis(500), 0, 0)))
                }
                val windowStart = Instant.parse(startingTime)
                val lead = (Duration.between(Instant.parse("2026-10-15T23:11:10Z"), windowStart).toMinutes() / 2).toInt() * 1000
                return Response.success(window(frame(windowStart.plusSeconds(1), 0, 0), frame(windowStart.plusSeconds(9), 5000 + lead, 5000)))
            } finally {
                inFlight--
            }
        }

        override suspend fun getDetails(gameId: String, startingTime: String): Response<LiveStatsDetailsResponse> =
            Response.error(404, "".toResponseBody())
    }

    private fun window(vararg frames: LiveStatsFrameDto) = LiveStatsWindowResponse(esportsGameId = "g1", frames = frames.toList())

    private fun frame(time: Instant, blueGold: Int, redGold: Int) = LiveStatsFrameDto(
        rfc460Timestamp = time.toString(),
        gameState = "in_game",
        blueTeam = LiveStatsTeamFrameDto(totalGold = blueGold),
        redTeam = LiveStatsTeamFrameDto(totalGold = redGold),
    )

    private companion object {
        const val FIRST_FRAMES = ""
    }
}
