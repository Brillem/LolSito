package com.example.lolsito.feature.match.ui

import app.cash.turbine.test
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.polling.MATCH_DETAIL_POLL_INTERVAL
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.FakeTeamsRepository
import com.example.lolsito.testutil.FakeUserPreferences
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
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMatchDetailRepository()
    private val preferences = FakeUserPreferences()

    // These series have no teams, so no history is ever asked for (see MatchDetailHistoryViewModelTest).
    private fun viewModel(matchId: String) = MatchDetailViewModel(
        matchId = matchId,
        repository = repository,
        preferences = preferences,
        teamsRepository = FakeTeamsRepository(),
        scheduleRepository = FakeScheduleRepository(),
        leaguesRepository = { emptyList() },
        zoneId = ZoneOffset.UTC,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial load shows loading then content`() = runTest(dispatcher) {
        repository.detail = detail("m1")

        val vm = viewModel("m1")
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Content(detail("m1")), awaitItem())
        }
        assertEquals(listOf("m1"), repository.requests)
    }

    @Test
    fun `error then retry shows loading and recovers`() = runTest(dispatcher) {
        repository.error = IOException("boom")

        val vm = viewModel("m1")
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Error("boom"), awaitItem())

            repository.error = null
            repository.detail = detail("m1")
            vm.retry()

            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Content(detail("m1")), awaitItem())
        }
        assertEquals(listOf("m1", "m1"), repository.requests)
    }

    @Test
    fun `blank error message falls back to a generic one`() = runTest(dispatcher) {
        repository.error = IOException("")

        val vm = viewModel("m1")
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Error("Something went wrong"), awaitItem())
        }
    }


    @Test
    fun `a completed match is loaded once however long the screen stays visible`() = runTest(dispatcher) {
        repository.detail = detail("m1")
        val vm = viewModel("m1")
        advanceUntilIdle()

        val polling = launch { vm.pollWhileVisible() }
        advance(MATCH_DETAIL_POLL_INTERVAL * 10)

        assertEquals(MatchDetailUiState.Content(detail("m1")), vm.uiState.value)
        assertEquals(listOf("m1"), repository.requests)
        polling.cancel()
    }

    @Test
    fun `an in-progress match reloads every interval while visible, without a spinner`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS, "Round 1")
        val vm = viewModel("m1")
        val states = mutableListOf<MatchDetailUiState>()
        backgroundScope.launch { vm.uiState.toList(states) }
        runCurrent() // the initial load is done; from here on only Content may follow
        val polling = launch { vm.pollWhileVisible() }

        repository.detail = detail("m1", MatchState.IN_PROGRESS, "Round 2")
        advance(MATCH_DETAIL_POLL_INTERVAL)
        repository.detail = detail("m1", MatchState.IN_PROGRESS, "Round 3")
        advance(MATCH_DETAIL_POLL_INTERVAL)
        polling.cancel()

        assertEquals(
            listOf(
                MatchDetailUiState.Content(detail("m1", MatchState.IN_PROGRESS, "Round 1")),
                MatchDetailUiState.Content(detail("m1", MatchState.IN_PROGRESS, "Round 2")),
                MatchDetailUiState.Content(detail("m1", MatchState.IN_PROGRESS, "Round 3")),
            ),
            states,
        )
        assertEquals(listOf("m1", "m1", "m1"), repository.requests)
    }

    @Test
    fun `polling stops once the match completes`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        val vm = viewModel("m1")
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.detail = detail("m1", MatchState.COMPLETED)
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(MatchState.COMPLETED, (vm.uiState.value as MatchDetailUiState.Content).detail.state)
        assertEquals(2, repository.requests.size)

        advance(MATCH_DETAIL_POLL_INTERVAL * 10)

        assertEquals(2, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `nothing is requested while the screen is not visible and polling resumes when it is again`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        val vm = viewModel("m1")
        advanceUntilIdle()

        advance(MATCH_DETAIL_POLL_INTERVAL * 5)
        assertEquals(1, repository.requests.size)

        var polling: Job = launch { vm.pollWhileVisible() }
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(2, repository.requests.size)

        polling.cancel() // the screen goes to the background
        advance(MATCH_DETAIL_POLL_INTERVAL * 5)
        assertEquals(2, repository.requests.size)

        polling = launch { vm.pollWhileVisible() } // and comes back
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(3, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `a failed background refresh keeps the last content and is retried at the next tick`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS, "Round 1")
        val vm = viewModel("m1")
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.error = IOException("offline")
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(MatchDetailUiState.Content(detail("m1", MatchState.IN_PROGRESS, "Round 1")), vm.uiState.value)
        assertEquals(2, repository.requests.size)

        repository.error = null
        repository.detail = detail("m1", MatchState.IN_PROGRESS, "Round 2")
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(MatchDetailUiState.Content(detail("m1", MatchState.IN_PROGRESS, "Round 2")), vm.uiState.value)
        polling.cancel()
    }

    @Test
    fun `no refresh is requested while the initial load is still in flight`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        repository.gate = CompletableDeferred()
        val vm = viewModel("m1")
        val polling = launch { vm.pollWhileVisible() }

        advance(MATCH_DETAIL_POLL_INTERVAL * 3)
        assertEquals(MatchDetailUiState.Loading, vm.uiState.value)
        assertEquals(1, repository.requests.size)

        repository.gate?.complete(Unit)
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertTrue(vm.uiState.value is MatchDetailUiState.Content)
        assertEquals(2, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `a slow refresh is never overlapped by the next tick`() = runTest(dispatcher) {
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        val vm = viewModel("m1")
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.gate = CompletableDeferred()
        advance(MATCH_DETAIL_POLL_INTERVAL * 4)
        assertEquals(2, repository.requests.size)

        repository.gate?.complete(Unit)
        repository.gate = null
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(3, repository.requests.size)
        polling.cancel()
    }

    @Test
    fun `an initial failure is not polled and retry still recovers`() = runTest(dispatcher) {
        repository.error = IOException("boom")
        val vm = viewModel("m1")
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        advance(MATCH_DETAIL_POLL_INTERVAL * 3)
        assertEquals(MatchDetailUiState.Error("boom"), vm.uiState.value)
        assertEquals(1, repository.requests.size)

        repository.error = null
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        vm.retry()
        advance(MATCH_DETAIL_POLL_INTERVAL)
        assertEquals(3, repository.requests.size) // the retry plus one refresh
        polling.cancel()
    }

    private suspend fun TestScope.advance(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    /** [blockName] only tells two snapshots of the same match apart. */
    @Test
    fun `results are shown when the mode is off`() = runTest(dispatcher) {
        repository.detail = detail("m1")

        val vm = viewModel("m1")
        advanceUntilIdle()

        assertEquals(MatchDetailUiState.Content(detail("m1"), resultsHidden = false), vm.uiState.value)
    }

    @Test
    fun `results start hidden when the mode is on and revealing shows them`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail("m1")

        val vm = viewModel("m1")
        advanceUntilIdle()
        assertEquals(MatchDetailUiState.Content(detail("m1"), resultsHidden = true), vm.uiState.value)

        vm.revealResult()
        assertEquals(MatchDetailUiState.Content(detail("m1"), resultsHidden = false), vm.uiState.value)
    }

    @Test
    fun `a reveal survives a refresh of the same screen`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail("m1", MatchState.IN_PROGRESS)
        val vm = viewModel("m1")
        advanceUntilIdle()
        vm.revealResult()

        val poll = launch { vm.pollWhileVisible() }
        advanceTimeBy(MATCH_DETAIL_POLL_INTERVAL + 1.milliseconds)
        runCurrent()
        poll.cancel()

        assertEquals(false, (vm.uiState.value as MatchDetailUiState.Content).resultsHidden)
        assertTrue(repository.requests.size >= 2)
    }

    @Test
    fun `a reveal does not persist and a new screen hides results again`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail("m1")
        val first = viewModel("m1")
        advanceUntilIdle()
        first.revealResult()

        val second = viewModel("m1")
        advanceUntilIdle()

        assertEquals(true, (second.uiState.value as MatchDetailUiState.Content).resultsHidden)
        assertTrue(preferences.savedNoSpoilers.isEmpty())
    }

    @Test
    fun `a reveal applies to its own series only`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail("m1")
        val one = viewModel("m1")
        val other = viewModel("m2")
        advanceUntilIdle()

        one.revealResult()

        assertEquals(true, (other.uiState.value as MatchDetailUiState.Content).resultsHidden)
    }

    @Test
    fun `an unreadable preference shows the results`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        preferences.readFails = true
        repository.detail = detail("m1")

        val vm = viewModel("m1")
        advanceUntilIdle()

        assertEquals(false, (vm.uiState.value as MatchDetailUiState.Content).resultsHidden)
    }

    @Test
    fun `retry keeps the reveal`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail("m1")
        val vm = viewModel("m1")
        advanceUntilIdle()
        vm.revealResult()

        vm.retry()
        advanceUntilIdle()

        assertEquals(false, (vm.uiState.value as MatchDetailUiState.Content).resultsHidden)
    }

    private fun detail(id: String, state: MatchState = MatchState.COMPLETED, blockName: String? = null) = MatchDetail(
        matchId = id,
        state = state,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        leagueImageUrl = null,
        blockName = blockName,
        teams = emptyList(),
        format = SeriesFormat(SeriesType.BEST_OF, 5),
        games = emptyList(),
    )

    private class FakeMatchDetailRepository : MatchDetailRepository {
        var detail: MatchDetail? = null
        var error: Throwable? = null
        val requests = mutableListOf<String>()

        /** When set, getMatchDetail suspends on it after counting the request. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getMatchDetail(matchId: String): MatchDetail {
            requests += matchId
            gate?.await()
            error?.let { throw it }
            return detail ?: error("No detail")
        }
    }
}
