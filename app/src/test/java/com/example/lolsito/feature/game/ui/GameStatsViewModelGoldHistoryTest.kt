package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.polling.GAME_STATS_POLL_INTERVAL
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.game.domain.GoldHistory
import com.example.lolsito.feature.game.domain.GoldPoint
import com.example.lolsito.feature.game.domain.GoldSample
import com.example.lolsito.feature.game.domain.TeamStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration as KotlinDuration

@OptIn(ExperimentalCoroutinesApi::class)
class GameStatsViewModelGoldHistoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val start = Instant.parse("2026-10-15T23:11:17Z")
    private val backfill = GoldHistory.of(start, listOf(GoldSample(start, 0, 0), GoldSample(start.plusSeconds(120), 5100, 5000)))
    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `stats show without waiting for the gold history, which then ends at the stats frame`() = runTest(dispatcher) {
        val pending = CompletableDeferred<GoldHistory?>()
        repository.history = { pending.await() }
        repository.stats = stats(seconds = 300, blueGold = 9000, redGold = 8000, finished = true)
        val vm = GameStatsViewModel("g1", repository)

        advanceUntilIdle()
        assertEquals(GameStatsUiState(LoadStatus.Content, repository.stats), vm.uiState.value)

        pending.complete(backfill)
        advanceUntilIdle()
        assertEquals(listOf(start.plusSeconds(300)), repository.historyUntil)
        assertEquals(
            listOf(GoldPoint(Duration.ZERO, 0), GoldPoint(Duration.ofSeconds(120), 100), GoldPoint(Duration.ofSeconds(300), 1000)),
            vm.uiState.value.goldHistory?.points,
        )
        assertEquals(repository.stats, vm.uiState.value.stats)
    }

    @Test
    fun `a gold history that fails or is missing leaves the stats as they are`() = runTest(dispatcher) {
        repository.history = { throw IOException("boom") }
        val failed = GameStatsViewModel("g1", repository)
        advanceUntilIdle()
        assertEquals(GameStatsUiState(LoadStatus.Content, repository.stats), failed.uiState.value)

        repository.history = { null }
        val missing = GameStatsViewModel("g1", repository)
        advanceUntilIdle()
        assertEquals(GameStatsUiState(LoadStatus.Content, repository.stats), missing.uiState.value)
    }

    @Test
    fun `a live game backfills once and then grows with each refresh`() = runTest(dispatcher) {
        val vm = GameStatsViewModel("g1", repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = stats(seconds = 330, blueGold = 9500, redGold = 10000)
        advance(GAME_STATS_POLL_INTERVAL)
        repository.stats = stats(seconds = 360, blueGold = 9500, redGold = 11000)
        advance(GAME_STATS_POLL_INTERVAL)

        assertEquals(1, repository.historyUntil.size)
        assertEquals(
            listOf(0, 100, 1000, -500, -1500),
            vm.uiState.value.goldHistory?.points?.map { it.difference },
        )
        assertEquals(Duration.ofSeconds(360), vm.uiState.value.goldHistory?.points?.last()?.gameTime)
        polling.cancel()
    }

    @Test
    fun `retry drops the shown history and asks for it again`() = runTest(dispatcher) {
        val vm = GameStatsViewModel("g1", repository)
        advanceUntilIdle()

        val pending = CompletableDeferred<GoldHistory?>()
        repository.history = { pending.await() }
        vm.retry()
        advanceUntilIdle()

        assertNull(vm.uiState.value.goldHistory)
        assertEquals(2, repository.historyUntil.size)
        pending.complete(backfill)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.goldHistory?.points?.size)
    }

    private fun TestScope.advance(duration: KotlinDuration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun stats(seconds: Long, blueGold: Int, redGold: Int, finished: Boolean = false) = GameStats(
        isFinished = finished,
        blueTeam = TeamStats(blueGold, 0, 0, 0, 0, emptyList()),
        redTeam = TeamStats(redGold, 0, 0, 0, 0, emptyList()),
        frameTime = start.plusSeconds(seconds),
    )

    private inner class FakeRepository : GameStatsRepository {
        var stats: GameStats = stats(seconds = 300, blueGold = 9000, redGold = 8000)
        var history: suspend () -> GoldHistory? = { backfill }
        val historyUntil = mutableListOf<Instant>()

        override suspend fun getGameStats(gameId: String): GameStats = stats

        override suspend fun getGoldHistory(gameId: String, until: Instant): GoldHistory? {
            historyUntil += until
            return history()
        }
    }
}
