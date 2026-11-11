package com.example.lolsito.feature.game.ui

import app.cash.turbine.test
import com.example.lolsito.core.polling.GAME_STATS_POLL_INTERVAL
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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


    @Test
    fun `a finished game is loaded once however long the screen stays visible`() = runTest(dispatcher) {
        repository.stats = stats
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()

        val polling = launch { vm.pollWhileVisible() }
        advance(GAME_STATS_POLL_INTERVAL * 10)

        assertEquals(GameStatsUiState(LoadStatus.Content, stats), vm.uiState.value)
        assertEquals(listOf("g1"), repository.requests)
        polling.cancel()
    }

    @Test
    fun `a live game reloads every interval while visible, without a spinner`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        val states = mutableListOf<GameStatsUiState>()
        backgroundScope.launch { vm.uiState.toList(states) }
        runCurrent() // the initial load is done; from here on only Content may follow
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = live(kills = 2)
        advance(GAME_STATS_POLL_INTERVAL)
        repository.stats = live(kills = 3)
        advance(GAME_STATS_POLL_INTERVAL)
        polling.cancel()

        assertEquals(
            listOf(
                GameStatsUiState(LoadStatus.Content, live(kills = 1)),
                GameStatsUiState(LoadStatus.Content, live(kills = 2)),
                GameStatsUiState(LoadStatus.Content, live(kills = 3)),
            ),
            states,
        )
        assertEquals(listOf("g1", "g1", "g1"), repository.requests)
    }

    @Test
    fun `polling stops once the game is finished`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = stats
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(GameStatsUiState(LoadStatus.Content, stats), vm.uiState.value)
        assertEquals(2, repository.requests.size)

        advance(GAME_STATS_POLL_INTERVAL * 10)

        assertEquals(2, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `nothing is requested while the screen is not visible and polling resumes when it is again`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()

        advance(GAME_STATS_POLL_INTERVAL * 5)
        assertEquals(1, repository.requests.size)

        var polling: Job = launch { vm.pollWhileVisible() }
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(2, repository.requests.size)

        polling.cancel() // the screen goes to the background
        advance(GAME_STATS_POLL_INTERVAL * 5)
        assertEquals(2, repository.requests.size)

        polling = launch { vm.pollWhileVisible() } // and comes back
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(3, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `a failed or empty background refresh keeps the last stats and is retried at the next tick`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.error = IOException("offline")
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(GameStatsUiState(LoadStatus.Content, live(kills = 1)), vm.uiState.value)

        repository.error = null
        repository.stats = null // the feed has no frames right now
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(GameStatsUiState(LoadStatus.Content, live(kills = 1)), vm.uiState.value)

        repository.stats = live(kills = 4)
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(GameStatsUiState(LoadStatus.Content, live(kills = 4)), vm.uiState.value)
        assertEquals(4, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `no refresh is requested while the initial load is still in flight`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        repository.gate = CompletableDeferred()
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        val polling = launch { vm.pollWhileVisible() }

        advance(GAME_STATS_POLL_INTERVAL * 3)
        assertEquals(GameStatsUiState(LoadStatus.Loading), vm.uiState.value)
        assertEquals(1, repository.requests.size)

        repository.gate?.complete(Unit)
        advance(GAME_STATS_POLL_INTERVAL)
        assertTrue(vm.uiState.value.status == LoadStatus.Content)
        assertEquals(2, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `a slow refresh is never overlapped by the next tick`() = runTest(dispatcher) {
        repository.stats = live(kills = 1)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.gate = CompletableDeferred()
        advance(GAME_STATS_POLL_INTERVAL * 4)
        assertEquals(2, repository.requests.size)

        repository.gate?.complete(Unit)
        repository.gate = null
        advance(GAME_STATS_POLL_INTERVAL)
        assertEquals(3, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `an empty or failed initial load is not polled`() = runTest(dispatcher) {
        repository.stats = null
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        advance(GAME_STATS_POLL_INTERVAL * 3)
        assertEquals(GameStatsUiState(LoadStatus.Empty), vm.uiState.value)
        assertEquals(1, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `a refresh brings fresh details along with the stats`() = runTest(dispatcher) {
        repository.stats = withDetails(live(kills = 1), gold = 1000)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = withDetails(live(kills = 2), gold = 2000)
        advance(GAME_STATS_POLL_INTERVAL)

        assertEquals(
            GameStatsUiState(LoadStatus.Content, withDetails(live(kills = 2), gold = 2000)),
            vm.uiState.value,
        )
        polling.cancel()
    }

    @Test
    fun `a refresh without details keeps the last good details but takes the new stats`() = runTest(dispatcher) {
        repository.stats = withDetails(live(kills = 1), gold = 1000)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = playersOnly(live(kills = 2)) // details feed failed or answered 204
        advance(GAME_STATS_POLL_INTERVAL)

        assertEquals(
            GameStatsUiState(LoadStatus.Content, withDetails(live(kills = 2), gold = 1000)),
            vm.uiState.value,
        )

        repository.stats = withDetails(live(kills = 3), gold = 3000) // and it recovers
        advance(GAME_STATS_POLL_INTERVAL)

        assertEquals(
            GameStatsUiState(LoadStatus.Content, withDetails(live(kills = 3), gold = 3000)),
            vm.uiState.value,
        )
        polling.cancel()
    }

    @Test
    fun `a game whose details never loaded stays as the window stats said`() = runTest(dispatcher) {
        repository.stats = playersOnly(live(kills = 1))
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.stats = playersOnly(live(kills = 2))
        advance(GAME_STATS_POLL_INTERVAL)

        assertEquals(GameStatsUiState(LoadStatus.Content, playersOnly(live(kills = 2))), vm.uiState.value)
        polling.cancel()
    }

    @Test
    fun `a retry does not carry details over from before it`() = runTest(dispatcher) {
        repository.stats = withDetails(live(kills = 1), gold = 1000)
        val vm = GameStatsViewModel(gameId = "g1", repository = repository)
        advanceUntilIdle()

        repository.stats = playersOnly(live(kills = 2))
        vm.retry()
        advanceUntilIdle()

        assertEquals(GameStatsUiState(LoadStatus.Content, playersOnly(live(kills = 2))), vm.uiState.value)
    }

    private fun player(id: Int, details: PlayerDetails? = null) =
        PlayerStats(id, "P$id", "Olaf", kills = 0, deaths = 0, assists = 0, creepScore = 0, level = 1, details = details)

    private fun playersOnly(base: GameStats) = base.copy(
        blueTeam = base.blueTeam.copy(players = listOf(player(1), player(2))),
        redTeam = base.redTeam.copy(players = listOf(player(6))),
    )

    private fun withDetails(base: GameStats, gold: Int): GameStats {
        fun details(id: Int) = PlayerDetails(
            itemIds = listOf(3047),
            primaryStyleId = 8000,
            secondaryStyleId = 8300,
            keystoneId = 8010,
            damageShare = 0.25,
            killParticipation = 0.5,
            totalGold = gold + id,
            wardsPlaced = 1,
            wardsDestroyed = 2,
        )
        return base.copy(
            blueTeam = base.blueTeam.copy(players = listOf(player(1, details(1)), player(2, details(2)))),
            redTeam = base.redTeam.copy(players = listOf(player(6, details(6)))),
        )
    }

    private suspend fun TestScope.advance(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun live(kills: Int) = stats.copy(
        isFinished = false,
        blueTeam = stats.blueTeam.copy(kills = kills),
    )

    private val stats = GameStats(
        isFinished = true,
        blueTeam = TeamStats(70731, 11, 9, 3, 1, emptyList()),
        redTeam = TeamStats(60000, 10, 3, 1, 0, emptyList()),
    )

    private class FakeGameStatsRepository : GameStatsRepository {
        var stats: GameStats? = null
        var error: Throwable? = null
        val requests = mutableListOf<String>()

        /** When set, getGameStats suspends on it after counting the request. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getGameStats(gameId: String): GameStats? {
            requests += gameId
            gate?.await()
            error?.let { throw it }
            return stats
        }
    }
}
