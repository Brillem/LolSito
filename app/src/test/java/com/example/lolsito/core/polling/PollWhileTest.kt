package com.example.lolsito.core.polling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PollWhileTest {

    private val interval = 30.seconds

    private suspend fun kotlinx.coroutines.test.TestScope.advance(by: Duration) {
        advanceTimeBy(by)
        runCurrent()
    }

    @Test
    fun `ticks once per interval and never before the first one`() = runTest {
        var ticks = 0
        val job = launch { pollWhile(interval) { ticks++ } }

        runCurrent()
        assertEquals(0, ticks)
        advance(29.seconds)
        assertEquals(0, ticks)
        advance(1.seconds)
        assertEquals(1, ticks)
        advance(interval * 2)
        assertEquals(3, ticks)
        job.cancel()
    }

    @Test
    fun `cancelling the caller stops the polling`() = runTest {
        var ticks = 0
        val job = launch { pollWhile(interval) { ticks++ } }
        advance(interval)
        assertEquals(1, ticks)

        job.cancel()
        advance(interval * 5)

        assertEquals(1, ticks)
    }

    @Test
    fun `does not tick while inactive and follows the flag afterwards`() = runTest {
        val active = MutableStateFlow(false)
        var ticks = 0
        val job = launch { pollWhile(interval, active) { ticks++ } }

        advance(interval * 3)
        assertEquals(0, ticks)

        active.value = true
        advance(interval)
        assertEquals(1, ticks)

        active.value = false
        advance(interval * 3)
        assertEquals(1, ticks)

        active.value = true
        advance(interval - 1.seconds)
        assertEquals("the wait restarts from the moment it became active", 1, ticks)
        advance(1.seconds)
        assertEquals(2, ticks)
        job.cancel()
    }

    @Test
    fun `a tick failure does not stop the polling`() = runTest {
        var ticks = 0
        val job = launch {
            pollWhile(interval) {
                ticks++
                if (ticks == 1) throw IOException("boom")
            }
        }

        advance(interval * 2)

        assertEquals(2, ticks)
        job.cancel()
    }

    @Test
    fun `a cancellation raised inside a tick ends the polling instead of being swallowed`() = runTest {
        var ticks = 0
        val job = launch {
            pollWhile(interval) {
                ticks++
                throw CancellationException("stop")
            }
        }

        advance(interval * 3)

        assertEquals(1, ticks)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `a slow tick delays the next one instead of overlapping it`() = runTest {
        var running = 0
        var maxRunning = 0
        var started = 0
        val job = launch {
            pollWhile(interval) {
                started++
                running++
                maxRunning = maxOf(maxRunning, running)
                delay(45.seconds)
                running--
            }
        }

        advance(interval) // tick 1 starts at 30 s and ends at 75 s
        assertEquals(1, started)
        advance(60.seconds) // 90 s: the next one waits 30 s after the end, so 105 s
        assertEquals(1, started)
        advance(15.seconds)
        assertEquals(2, started)
        assertEquals(1, maxRunning)
        assertFalse(job.isCompleted)
        job.cancel()
    }
}
