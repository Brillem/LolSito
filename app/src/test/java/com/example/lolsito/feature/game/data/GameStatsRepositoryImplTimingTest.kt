package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.testutil.FakeLiveStatsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

/** The two feeds run concurrently and the details feed can never hold the stats back for long; all on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameStatsRepositoryImplTimingTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-11T16:11:15.900Z"), ZoneOffset.UTC)
    private val api = FakeLiveStatsApi()
    private val repository = GameStatsRepositoryImpl(api, clock)

    private fun hasDetails(stats: GameStats) =
        (stats.blueTeam.players + stats.redTeam.players).any { it.details != null }

    @Test
    fun `a details answer within the wait limit is included`() = runTest {
        api.details = { delay(2.seconds); FakeLiveStatsApi.detailsResponse() }

        val stats = repository.getGameStats("1")!!

        assertTrue(hasDetails(stats))
        assertEquals(2_000, currentTime)
    }

    @Test
    fun `slow details are given up on after the wait limit and the stats come without them`() = runTest {
        api.details = { delay(10.seconds); FakeLiveStatsApi.detailsResponse() }

        val stats = repository.getGameStats("1")!!

        assertTrue(!hasDetails(stats))
        assertEquals(31850, stats.blueTeam.totalGold)
        assertEquals(DETAILS_WAIT_LIMIT.inWholeMilliseconds, currentTime)
        assertTrue(api.detailsCancelled)
    }

    @Test
    fun `details that never answer do not hold the stats back beyond the wait limit`() = runTest {
        api.details = { FakeLiveStatsApi.never() }

        val stats = repository.getGameStats("1")!!

        assertTrue(!hasDetails(stats))
        assertEquals(31850, stats.blueTeam.totalGold)
        assertEquals(DETAILS_WAIT_LIMIT.inWholeMilliseconds, currentTime)
        assertTrue(api.detailsCancelled)
    }

    @Test
    fun `the wait limit counts from the window answer, so a slow window and slow details overlap`() = runTest {
        api.window = { delay(5.seconds); FakeLiveStatsApi.windowResponse() }
        api.details = { delay(4.seconds); FakeLiveStatsApi.detailsResponse() }

        val stats = repository.getGameStats("1")!!

        assertTrue(hasDetails(stats))
        assertEquals(5_000, currentTime)
    }

    @Test
    fun `details that answer with an error before the window do not matter`() = runTest {
        api.window = { delay(1.seconds); FakeLiveStatsApi.windowResponse() }
        api.details = { throw IOException("down") }

        val stats = repository.getGameStats("1")!!

        assertTrue(!hasDetails(stats))
        assertEquals(1_000, currentTime)
    }

    @Test
    fun `both requests use the same starting time`() = runTest {
        repository.getGameStats("1")

        assertEquals(listOf("2026-11-11T16:10:10Z"), api.windowStartingTimes)
        assertEquals(listOf("2026-11-11T16:10:10Z"), api.detailsStartingTimes)
    }

    @Test
    fun `a window error is thrown at once and the details request is cancelled`() = runTest {
        api.window = { Response.error(404, "".toResponseBody()) }
        api.details = { FakeLiveStatsApi.never() }

        try {
            repository.getGameStats("1")
            fail("Expected an exception")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
        assertEquals(0, currentTime)
        assertTrue(api.detailsCancelled || api.detailsStartingTimes.isEmpty()) // cancelled, or cancelled before it started
    }

    @Test
    fun `a window without frames returns null at once and the details request is cancelled`() = runTest {
        api.window = { Response.success<LiveStatsWindowResponse>(null) }
        api.details = { FakeLiveStatsApi.never() }

        assertNull(repository.getGameStats("1"))
        assertEquals(0, currentTime)
        assertTrue(api.detailsCancelled || api.detailsStartingTimes.isEmpty()) // cancelled, or cancelled before it started
    }

    @Test
    fun `cancelling the caller cancels both requests and is not swallowed`() = runTest {
        api.window = { FakeLiveStatsApi.never() }
        api.details = { FakeLiveStatsApi.never() }

        val job = launch { repository.getGameStats("1") }
        runCurrent()
        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(api.windowCancelled)
        assertTrue(api.detailsCancelled)
    }

    @Test
    fun `cancelling the caller while it waits for details cancels the details request`() = runTest {
        api.details = { FakeLiveStatsApi.never() }

        val job = launch { repository.getGameStats("1") }
        runCurrent()
        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(api.detailsCancelled)
        assertEquals(0, currentTime)
    }
}
