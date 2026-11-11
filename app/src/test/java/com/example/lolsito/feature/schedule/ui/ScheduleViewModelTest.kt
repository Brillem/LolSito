package com.example.lolsito.feature.schedule.ui

import app.cash.turbine.test
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.testutil.FakeUserPreferences
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
    private var leaguesError: Throwable? = null
    private val leaguesRepository = LeaguesRepository {
        leaguesError?.let { throw it }
        listOf(
            League(WORLDS, "worlds", "Worlds", "INTERNATIONAL", null),
            League(LCK, "lck", "LCK", "KOREA", null),
        )
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val preferences = FakeUserPreferences()

    private fun viewModel(zoneId: ZoneId = ZoneId.of("UTC")) = ScheduleViewModel(
        repository = repository,
        leaguesRepository = leaguesRepository,
        preferences = preferences,
        zoneId = zoneId,
        defaultLeagueIds = setOf(WORLDS),
    )

    @Test
    fun `initial load shows loading then content`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1), event("2", 2)), older = "o1", newer = "n1")

        val vm = viewModel()
        vm.uiState.test {
            assertEquals(LoadStatus.Loading, awaitItem().status)
            var content = awaitItem()
            while (content.status == LoadStatus.Loading) content = awaitItem()
            assertEquals(LoadStatus.Content, content.status)
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
        assertTrue(vm.uiState.value.status is LoadStatus.Error)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        vm.retry()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `leagues failure shows error`() = runTest(dispatcher) {
        leaguesError = IOException("down")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is LoadStatus.Error)
    }

    @Test
    fun `empty page without cursors shows empty state`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(LoadStatus.Empty, state.status)
        assertFalse(state.canLoadOlder)
        assertFalse(state.canLoadNewer)
    }

    @Test
    fun `empty page with an older cursor still offers paging back`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList(), older = "o1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        val empty = vm.uiState.value
        assertEquals(LoadStatus.Content, empty.status)
        assertTrue(empty.days.isEmpty())
        assertTrue(empty.canLoadOlder)
        assertFalse(empty.canLoadNewer)

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf("1"), vm.uiState.value.days.flatMap { d -> d.events.map { it.matchId } })
    }

    @Test
    fun `empty page with a newer cursor still offers paging forward`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList(), newer = "n1")
        val vm = viewModel()
        advanceUntilIdle()

        val empty = vm.uiState.value
        assertEquals(LoadStatus.Content, empty.status)
        assertTrue(empty.days.isEmpty())
        assertFalse(empty.canLoadOlder)
        assertTrue(empty.canLoadNewer)
    }

    @Test
    fun `ui state exposes the zone the view model groups days with`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList())
        val caracas = ZoneId.of("America/Caracas")
        val vm = viewModel(zoneId = caracas)
        assertEquals(caracas, vm.uiState.value.zoneId)
        advanceUntilIdle()
        assertEquals(caracas, vm.uiState.value.zoneId)
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
        assertEquals("older down", failed.olderError)
        assertNull(failed.newerError)
        assertFalse(failed.isPaging)
        assertFalse(failed.isLoadingOlder)
        assertFalse(failed.isLoadingNewer)
        assertEquals(LoadStatus.Content, failed.status)
        assertEquals(listOf("2"), failed.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)), older = null)
        vm.loadOlder()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.olderError)
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
        assertEquals("newer down", failed.newerError)
        assertNull(failed.olderError)
        assertFalse(failed.isPaging)
        assertFalse(failed.isLoadingOlder)
        assertFalse(failed.isLoadingNewer)
        assertEquals(LoadStatus.Content, failed.status)
        assertEquals(listOf("2"), failed.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("3", 3)), newer = null)
        vm.loadNewer()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.newerError)
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
        assertFalse(vm.uiState.value.isLoadingNewer)
        assertTrue(vm.uiState.value.isPaging)
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
        assertFalse(vm.uiState.value.isPaging)
    }

    @Test
    fun `a paging error stays with its direction until that direction is retried`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1", newer = "n1")
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("3", 3)), newer = "n2")
        val vm = viewModel()
        advanceUntilIdle()

        repository.scheduleError = IOException("older down")
        vm.loadOlder()
        advanceUntilIdle()
        assertEquals("older down", vm.uiState.value.olderError)

        repository.scheduleError = null
        vm.loadNewer()
        advanceUntilIdle()
        assertEquals("older down", vm.uiState.value.olderError)
        assertNull(vm.uiState.value.newerError)

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)))
        vm.loadOlder()
        advanceUntilIdle()
        assertNull(vm.uiState.value.olderError)
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.uiState.value.olderError)
    }

    @Test
    fun `reloading clears paging errors in both directions`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("2", 2)), older = "o1", newer = "n1")
        val vm = viewModel()
        advanceUntilIdle()
        repository.scheduleError = IOException("down")
        vm.loadOlder()
        advanceUntilIdle()
        vm.loadNewer()
        advanceUntilIdle()
        assertEquals("down", vm.uiState.value.olderError)
        assertEquals("down", vm.uiState.value.newerError)

        repository.scheduleError = null
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.olderError)
        assertNull(vm.uiState.value.newerError)
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
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
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

        assertEquals(LoadStatus.Empty, vm.uiState.value.status)
        assertEquals(requestsBefore, repository.requests.size)
    }

    @Test
    fun `nothing stored keeps the default selection and saves nothing`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(WORLDS), vm.uiState.value.selectedLeagueIds)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf(setOf(WORLDS) to null), repository.requests)
        assertTrue(preferences.savedScheduleLeagueIds.isEmpty())
    }

    @Test
    fun `stored selection is restored and loaded without requesting the default first`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK)
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("7", 7)))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(setOf(LCK), state.selectedLeagueIds)
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(listOf("7"), state.days.flatMap { d -> d.events.map { it.matchId } })
        assertEquals(listOf(setOf(LCK) to null), repository.requests)
        assertEquals(1, repository.scheduleCalls)
        assertTrue("restoring does not write back", preferences.savedScheduleLeagueIds.isEmpty())
    }

    @Test
    fun `the selection is never the default while the stored one is being restored`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK)
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("7", 7)))
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.status == LoadStatus.Loading && state.leagues.isEmpty()) {
                assertEquals(setOf(WORLDS), state.selectedLeagueIds)
                state = awaitItem()
            }
            // Once the league chips can be shown, they already carry the restored selection.
            assertEquals(setOf(LCK), state.selectedLeagueIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stored ids that are no longer leagues are ignored`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK, "gone")
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("7", 7)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(LCK), vm.uiState.value.selectedLeagueIds)
        assertEquals(listOf(setOf(LCK) to null), repository.requests)
    }

    @Test
    fun `a stored selection with no known league falls back to the default`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf("gone")
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(WORLDS), vm.uiState.value.selectedLeagueIds)
        assertEquals(listOf(setOf(WORLDS) to null), repository.requests)
    }

    @Test
    fun `a stored empty selection is restored as deselected without any request`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = emptySet()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), vm.uiState.value.selectedLeagueIds)
        assertEquals(LoadStatus.Empty, vm.uiState.value.status)
        assertEquals(0, repository.scheduleCalls)
    }

    @Test
    fun `toggling a league saves the new selection each time`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("9", 9)))
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("8", 8)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLeague(LCK)
        advanceUntilIdle()
        vm.toggleLeague(WORLDS)
        advanceUntilIdle()
        vm.toggleLeague(LCK)
        advanceUntilIdle()

        assertEquals(listOf(setOf(WORLDS, LCK), setOf(LCK), emptySet()), preferences.savedScheduleLeagueIds)
        assertEquals(emptySet<String>(), vm.uiState.value.selectedLeagueIds)
    }

    @Test
    fun `a toggle made before the stored selection is read wins over it`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK)
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("9", 9)))
        val vm = viewModel()

        vm.toggleLeague(LCK)
        advanceUntilIdle()

        assertEquals(setOf(WORLDS, LCK), vm.uiState.value.selectedLeagueIds)
        assertEquals(listOf(setOf(WORLDS, LCK) to null), repository.requests)
        assertEquals(listOf(setOf(WORLDS, LCK)), preferences.savedScheduleLeagueIds)
    }

    @Test
    fun `a failure reading preferences falls back to the default selection`() = runTest(dispatcher) {
        preferences.readFails = true
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(WORLDS), vm.uiState.value.selectedLeagueIds)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf(setOf(WORLDS) to null), repository.requests)
    }

    @Test
    fun `a failure writing preferences does not stop the toggle from loading`() = runTest(dispatcher) {
        preferences.writeFails = true
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("9", 9)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLeague(LCK)
        advanceUntilIdle()

        assertEquals(setOf(WORLDS, LCK), vm.uiState.value.selectedLeagueIds)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf("9"), vm.uiState.value.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(preferences.savedScheduleLeagueIds.isEmpty())
    }

    @Test
    fun `a restore that waits for the leagues still happens after a leagues retry`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK)
        leaguesError = IOException("down")
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("7", 7)))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is LoadStatus.Error)
        assertEquals(0, repository.scheduleCalls)

        leaguesError = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(setOf(LCK), vm.uiState.value.selectedLeagueIds)
        assertEquals(listOf(setOf(LCK) to null), repository.requests)
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
        match = Match(id = id, teams = emptyList(), format = null),
    )

    private class FakeScheduleRepository : ScheduleRepository {
        val pages = mutableMapOf<Pair<Set<String>, String?>, SchedulePage>()
        val requests = mutableListOf<Pair<Set<String>, String?>>()
        var scheduleError: Throwable? = null
        var scheduleCalls = 0

        /** When set, getSchedule suspends on it after counting the call. */
        var gate: CompletableDeferred<Unit>? = null

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
