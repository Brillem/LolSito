package com.example.lolsito.feature.schedule.ui

import app.cash.turbine.test
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeScheduleRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ScheduleViewModel(
        repository = repository,
        zoneId = ZoneId.of("UTC"),
        defaultLeagueIds = setOf(WORLDS),
    )

    @Test
    fun `initial load shows loading then content`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1), event("2", 2)), older = "o1", newer = "n1")

        val vm = viewModel()
        vm.uiState.test {
            assertEquals(ScheduleStatus.Loading, awaitItem().status)
            var content = awaitItem()
            while (content.status == ScheduleStatus.Loading) content = awaitItem()
            assertEquals(ScheduleStatus.Content, content.status)
            assertEquals(listOf("1", "2"), content.days.flatMap { d -> d.events.map { it.matchId } })
            assertEquals(2, content.leagues.size)
            assertEquals(setOf(WORLDS), content.selectedLeagueIds)
            assertTrue(content.canLoadOlder)
            assertTrue(content.canLoadNewer)
        }
    }

    @Test
    fun `error then retry recovers`() = runTest(dispatcher) {
        repository.scheduleError = IOException("boom")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is ScheduleStatus.Error)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        vm.retry()
        advanceUntilIdle()

        assertEquals(ScheduleStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `leagues failure shows error`() = runTest(dispatcher) {
        repository.leaguesError = IOException("down")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is ScheduleStatus.Error)
    }

    @Test
    fun `empty page shows empty state`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ScheduleStatus.Empty, vm.uiState.value.status)
    }

    @Test
    fun `load older and newer merge without duplicates`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2), event("3", 3)), older = "o1", newer = "n1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1), event("2", 2)), older = null, newer = "x")
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("3", 3), event("4", 4)), older = "y", newer = "n2")
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        advanceUntilIdle()
        vm.loadNewer()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("1", "2", "3", "4"), state.days.flatMap { d -> d.events.map { it.matchId } })
        assertFalse(state.canLoadOlder)
        assertTrue(state.canLoadNewer)

        repository.pages[key(setOf(WORLDS), "n2")] = page(emptyList(), newer = null)
        vm.loadNewer()
        advanceUntilIdle()
        assertEquals(setOf(WORLDS) to "n2", repository.requests.last())
    }

    @Test
    fun `load older failure keeps content and a retry succeeds`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1", newer = "n1")
        val vm = viewModel()
        advanceUntilIdle()

        repository.scheduleError = IOException("older down")
        vm.loadOlder()
        advanceUntilIdle()

        val failed = vm.uiState.value
        assertEquals("older down", failed.pagingError)
        assertFalse(failed.isLoadingOlder)
        assertFalse(failed.isLoadingNewer)
        assertEquals(ScheduleStatus.Content, failed.status)
        assertEquals(listOf("2"), failed.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)), older = null)
        vm.loadOlder()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.pagingError)
        assertEquals(listOf("1", "2"), recovered.days.flatMap { d -> d.events.map { it.matchId } })
        assertFalse(recovered.canLoadOlder)
        assertTrue(recovered.canLoadNewer)
        assertEquals(setOf(WORLDS) to "o1", repository.requests.last())
    }

    @Test
    fun `load newer failure keeps content and a retry succeeds`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1", newer = "n1")
        val vm = viewModel()
        advanceUntilIdle()

        repository.scheduleError = IOException("newer down")
        vm.loadNewer()
        advanceUntilIdle()

        val failed = vm.uiState.value
        assertEquals("newer down", failed.pagingError)
        assertFalse(failed.isLoadingOlder)
        assertFalse(failed.isLoadingNewer)
        assertEquals(ScheduleStatus.Content, failed.status)
        assertEquals(listOf("2"), failed.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("3", 3)), newer = null)
        vm.loadNewer()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.pagingError)
        assertEquals(listOf("2", "3"), recovered.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(recovered.canLoadOlder)
        assertFalse(recovered.canLoadNewer)
        assertEquals(setOf(WORLDS) to "n1", repository.requests.last())
    }

    @Test
    fun `load older while a page load is in flight does not request again`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1", newer = "n1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)), older = "o2")
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.loadOlder()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoadingOlder)
        val callsInFlight = repository.scheduleCalls

        vm.loadOlder()
        vm.loadNewer()
        advanceUntilIdle()
        assertEquals(callsInFlight, repository.scheduleCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(callsInFlight, repository.scheduleCalls)
        assertEquals(listOf("1", "2"), vm.uiState.value.days.flatMap { d -> d.events.map { it.matchId } })
        assertFalse(vm.uiState.value.isLoadingOlder)
    }

    @Test
    fun `load older while the initial load is in flight does not request again`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.retry()
        advanceUntilIdle()
        val callsInFlight = repository.scheduleCalls

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(callsInFlight, repository.scheduleCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(callsInFlight, repository.scheduleCalls)
        assertEquals(ScheduleStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `changing league filter resets pagination tokens`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1", newer = "n1")
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("9", 9)), older = null, newer = null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLeague(LCK)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(setOf(WORLDS, LCK), state.selectedLeagueIds)
        assertEquals(listOf("9"), state.days.flatMap { d -> d.events.map { it.matchId } })
        assertFalse(state.canLoadOlder)
        assertFalse(state.canLoadNewer)
        assertEquals(setOf(WORLDS, LCK) to null, repository.requests.last())
    }

    @Test
    fun `deselecting all leagues shows empty without request`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        val requestsBefore = repository.requests.size

        vm.toggleLeague(WORLDS)
        advanceUntilIdle()

        assertEquals(ScheduleStatus.Empty, vm.uiState.value.status)
        assertEquals(requestsBefore, repository.requests.size)
    }

    private fun key(ids: Set<String>, token: String?) = ids to token

    private fun page(events: List<ScheduleEvent>, older: String? = null, newer: String? = null) =
        SchedulePage(events, older, newer)

    private fun event(id: String, hour: Long) = ScheduleEvent(
        startTime = Instant.parse("2026-10-20T00:00:00Z").plusSeconds(hour * 3600),
        state = MatchState.UNSTARTED,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(id = id, teams = emptyList(), bestOf = null),
    )

    private class FakeScheduleRepository : ScheduleRepository {
        val pages = mutableMapOf<Pair<Set<String>, String?>, SchedulePage>()
        val requests = mutableListOf<Pair<Set<String>, String?>>()
        var scheduleError: Throwable? = null
        var leaguesError: Throwable? = null
        var scheduleCalls = 0

        /** When set, getSchedule suspends on it after counting the call. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getLeagues(): List<League> {
            leaguesError?.let { throw it }
            return listOf(
                League(WORLDS, "worlds", "Worlds", "INTERNATIONAL", null),
                League(LCK, "lck", "LCK", "KOREA", null),
            )
        }

        override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
            scheduleCalls++
            gate?.await()
            scheduleError?.let { throw it }
            val key = leagueIds.toSet() to pageToken
            requests += key
            return pages[key] ?: error("No page for $key")
        }
    }

    private companion object {
        const val WORLDS = "98767975604431411"
        const val LCK = "98767991310872058"
    }
}
