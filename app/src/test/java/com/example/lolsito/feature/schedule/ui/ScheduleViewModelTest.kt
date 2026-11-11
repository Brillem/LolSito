package com.example.lolsito.feature.schedule.ui

import app.cash.turbine.test
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.polling.LIVE_SECTION_POLL_INTERVAL
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    private val liveRepository = FakeLiveMatchesRepository()
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
        liveRepository = liveRepository,
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


    @Test
    fun `without live matches the schedule behaves as before and the live section is empty`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1), event("2", 2)), older = "o1")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(listOf("1", "2"), state.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(state.canLoadOlder)
        assertTrue(state.liveEvents.isEmpty())
        assertEquals(1, repository.scheduleCalls)
        assertEquals(1, liveRepository.calls)
    }

    @Test
    fun `the live section lists the live matches of the selected leagues only`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("w2", WORLDS, 2), live("k1", LCK, 1), live("w1", WORLDS, 0))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("w1", "w2"), vm.uiState.value.liveEvents.map { it.matchId })

        vm.toggleLeague(LCK)
        advanceUntilIdle()
        assertEquals(listOf("w1", "k1", "w2"), vm.uiState.value.liveEvents.map { it.matchId })

        vm.toggleLeague(WORLDS)
        vm.toggleLeague(LCK)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.liveEvents.isEmpty())
        assertEquals("toggling a league does not refetch the live matches", 1, liveRepository.calls)
    }

    @Test
    fun `a stored selection filters the live section like the schedule`() = runTest(dispatcher) {
        preferences.scheduleLeagueIds = setOf(LCK)
        repository.pages[key(setOf(LCK), null)] = page(listOf(event("7", 7)))
        liveRepository.events = listOf(live("w1", WORLDS, 0), live("k1", LCK, 1))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(LCK), vm.uiState.value.selectedLeagueIds)
        assertEquals(listOf("k1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(listOf(setOf(LCK) to null), repository.requests)
    }

    @Test
    fun `a live failure hides the section and leaves the schedule intact`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), newer = "n1")
        liveRepository.error = IOException("live down")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(listOf("1"), state.days.flatMap { d -> d.events.map { it.matchId } })
        assertTrue(state.canLoadNewer)
        assertTrue(state.liveEvents.isEmpty())
        assertEquals(null, state.liveMatchIds)
    }

    @Test
    fun `a live request that never answers does not block the schedule`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertTrue(vm.uiState.value.liveEvents.isEmpty())
    }

    @Test
    fun `retry reloads the live section together with the schedule`() = runTest(dispatcher) {
        repository.scheduleError = IOException("boom")
        liveRepository.error = IOException("live down")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is LoadStatus.Error)
        assertTrue(vm.uiState.value.liveEvents.isEmpty())

        repository.scheduleError = null
        liveRepository.error = null
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("w1", WORLDS, 0))
        vm.retry()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(2, liveRepository.calls)
    }

    @Test
    fun `a match is live in the day list when the live feed says so, whatever the schedule says`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(event("started", 1), event("ended", 2, MatchState.IN_PROGRESS), event("later", 3)),
        )
        liveRepository.events = listOf(live("started", WORLDS, 1))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        val byId = state.days.flatMap { it.events }.associateBy { it.matchId }
        assertTrue(state.isLive(byId.getValue("started")))
        assertFalse(state.isLive(byId.getValue("ended")))
        assertFalse(state.isLive(byId.getValue("later")))
    }

    @Test
    fun `without live data the schedule state decides what is live`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(event("1", 1, MatchState.IN_PROGRESS), event("2", 2)),
        )
        liveRepository.error = IOException("live down")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        val byId = state.days.flatMap { it.events }.associateBy { it.matchId }
        assertTrue(state.isLive(byId.getValue("1")))
        assertFalse(state.isLive(byId.getValue("2")))
    }

    @Test
    fun `live section keys are unique even when the feed repeats a match`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("1", WORLDS, 1), live("1", WORLDS, 1))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        val dayKeys = state.days.flatMap { it.keys }
        assertEquals(2, state.liveKeys.size)
        assertEquals(state.liveKeys.size, state.liveKeys.toSet().size)
        assertTrue((state.liveKeys + dayKeys).let { it.size == it.toSet().size })
    }

    @Test
    fun `the live section refreshes every interval while visible and keeps the schedule untouched`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        val vm = viewModel()
        advanceUntilIdle()
        val daysBefore = vm.uiState.value.days
        val polling = launch { vm.pollWhileVisible() }

        liveRepository.events = listOf(live("w1", WORLDS, 0))
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })

        liveRepository.events = emptyList()
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertTrue(vm.uiState.value.liveEvents.isEmpty())

        assertEquals(3, liveRepository.calls)
        assertEquals(1, repository.scheduleCalls)
        assertEquals(daysBefore, vm.uiState.value.days)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        polling.cancel()
    }

    @Test
    fun `nothing is refreshed while the screen is not visible and polling resumes when it is again`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        advance(LIVE_SECTION_POLL_INTERVAL * 5)
        assertEquals(1, liveRepository.calls)

        var polling: Job = launch { vm.pollWhileVisible() }
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(2, liveRepository.calls)

        polling.cancel() // the screen goes to the background
        advance(LIVE_SECTION_POLL_INTERVAL * 5)
        assertEquals(2, liveRepository.calls)

        polling = launch { vm.pollWhileVisible() } // and comes back
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(3, liveRepository.calls)
        polling.cancel()
    }

    @Test
    fun `a failed refresh keeps the last live section and is retried at the next tick`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("w1", WORLDS, 0))
        val vm = viewModel()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        liveRepository.error = IOException("offline")
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(LoadStatus.Content, vm.uiState.value.status)

        liveRepository.error = null
        liveRepository.events = listOf(live("w2", WORLDS, 1))
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(listOf("w2"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(3, liveRepository.calls)
        polling.cancel()
    }

    @Test
    fun `a tick is skipped while a live request is in flight`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.gate = CompletableDeferred() // the initial live request never answers
        val vm = viewModel()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        advance(LIVE_SECTION_POLL_INTERVAL * 4)

        assertEquals(1, liveRepository.calls)
        polling.cancel()
    }

    @Test
    fun `refreshing the live section does not clear a page that is loading`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("0", 0)))
        val vm = viewModel()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }

        repository.gate = CompletableDeferred()
        vm.loadOlder()
        liveRepository.events = listOf(live("w1", WORLDS, 0))
        advance(LIVE_SECTION_POLL_INTERVAL)

        assertTrue(vm.uiState.value.isLoadingOlder)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })
        polling.cancel()
    }

    private suspend fun TestScope.advance(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun live(id: String, leagueId: String, hour: Long) =
        event(id, hour, MatchState.IN_PROGRESS).copy(leagueId = leagueId)

    private fun key(ids: Set<String>, token: String?) = ids to token

    private fun page(events: List<ScheduleEvent>, older: String? = null, newer: String? = null) =
        SchedulePage(events, older, newer)

    private fun event(id: String, hour: Long, state: MatchState = MatchState.UNSTARTED) = ScheduleEvent(
        startTime = Instant.parse("2026-10-20T00:00:00Z").plusSeconds(hour * 3600),
        state = state,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(id = id, teams = emptyList(), format = null),
    )

    private class FakeLiveMatchesRepository : LiveMatchesRepository {
        var events: List<ScheduleEvent> = emptyList()
        var error: Throwable? = null
        var calls = 0

        /** When set, getLiveMatches suspends on it after counting the call. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getLiveMatches(): List<ScheduleEvent> {
            calls++
            gate?.await()
            error?.let { throw it }
            return events
        }
    }

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
