package com.example.lolsito.feature.game.ui

import app.cash.turbine.test
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.game.domain.TeamStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GameStatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeGameStatsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial load shows loading then content`() = runTest(dispatcher) {
        repository.stats = stats

        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        vm.uiState.test {
            assertEquals(GameStatsUiState(LoadStatus.Loading), awaitItem())
            assertEquals(GameStatsUiState(LoadStatus.Content, stats), awaitItem())
        }
        assertEquals(listOf("g1"), repository.requests)
    }

    @Test
    fun `no stats shows the empty state`() = runTest(dispatcher) {
        repository.stats = null

        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        vm.uiState.test {
            assertEquals(GameStatsUiState(LoadStatus.Loading), awaitItem())
            assertEquals(GameStatsUiState(LoadStatus.Empty), awaitItem())
        }
    }

    @Test
    fun `error then retry shows loading and recovers`() = runTest(dispatcher) {
        repository.error = IOException("boom")

        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        vm.uiState.test {
            assertEquals(GameStatsUiState(LoadStatus.Loading), awaitItem())
            assertEquals(GameStatsUiState(LoadStatus.Error("boom")), awaitItem())

            repository.error = null
            repository.stats = stats
            vm.retry()

            assertEquals(GameStatsUiState(LoadStatus.Loading), awaitItem())
            assertEquals(GameStatsUiState(LoadStatus.Content, stats), awaitItem())
        }
        assertEquals(listOf("g1", "g1"), repository.requests)
    }

    @Test
    fun `blank error message falls back to a generic one`() = runTest(dispatcher) {
        repository.error = IOException("")

        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        vm.uiState.test {
            assertEquals(GameStatsUiState(LoadStatus.Loading), awaitItem())
            assertEquals(GameStatsUiState(LoadStatus.Error("Something went wrong")), awaitItem())
        }
    }

    private val stats = GameStats(
        isFinished = true,
        blueTeam = TeamStats(70731, 11, 9, 3, 1, emptyList()),
        redTeam = TeamStats(60000, 10, 3, 1, 0, emptyList()),
    )

    private class FakeGameStatsRepository : GameStatsRepository {
        var stats: GameStats? = null
        var error: Throwable? = null
        val requests = mutableListOf<String>()

        override suspend fun getGameStats(gameId: String): GameStats? {
            requests += gameId
            error?.let { throw it }
            return stats
        }
    }
}
