package com.example.lolsito.feature.schedule.ui

import app.cash.turbine.test
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
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
            assertEquals(listOf("1", "2"), content.upcomingIds())
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
        assertTrue(empty.upcomingDays.isEmpty())
        assertTrue(empty.resultDays.isEmpty())
        assertTrue(empty.canLoadOlder)
        assertFalse(empty.canLoadNewer)

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf("1"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `empty page with a newer cursor still offers paging forward`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(emptyList(), newer = "n1")
        val vm = viewModel()
        advanceUntilIdle()

        val empty = vm.uiState.value
        assertEquals(LoadStatus.Content, empty.status)
        assertTrue(empty.upcomingDays.isEmpty())
        assertTrue(empty.resultDays.isEmpty())
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
        assertEquals(listOf("1", "2", "3", "4"), state.upcomingIds())
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
        assertEquals(listOf("2"), failed.upcomingIds())
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("1", 1)), older = null)
        vm.loadOlder()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.olderError)
        assertEquals(listOf("1", "2"), recovered.upcomingIds())
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
        assertEquals(listOf("2"), failed.upcomingIds())
        assertTrue(failed.canLoadOlder)
        assertTrue(failed.canLoadNewer)

        repository.scheduleError = null
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("3", 3)), newer = null)
        vm.loadNewer()
        advanceUntilIdle()

        val recovered = vm.uiState.value
        assertNull(recovered.newerError)
        assertEquals(listOf("2", "3"), recovered.upcomingIds())
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
        assertEquals(listOf("1", "2"), vm.uiState.value.upcomingIds())
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
        assertEquals(listOf("9"), state.upcomingIds())
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
        assertEquals(listOf("7"), state.upcomingIds())
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
        assertEquals(listOf("9"), vm.uiState.value.upcomingIds())
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
        assertEquals(listOf("1", "2"), state.upcomingIds())
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
        assertEquals(listOf("1"), state.upcomingIds())
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
    fun `a match is live only in the live section when the live feed says so, whatever the schedule says`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(event("started", 1), event("ended", 2, MatchState.IN_PROGRESS), event("later", 3)),
        )
        liveRepository.events = listOf(live("started", WORLDS, 1))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("started"), state.liveEvents.map { it.matchId })
        assertEquals(listOf("later"), state.upcomingIds())
        assertEquals("not in the feed any more, so it has finished", listOf("ended"), state.resultIds())
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
        assertEquals(listOf("1"), state.liveEvents.map { it.matchId })
        assertEquals(listOf("2"), state.upcomingIds())
        assertTrue(state.resultIds().isEmpty())
    }

    @Test
    fun `upcoming matches come soonest first and results most recent first`() = runTest(dispatcher) {
        val done = MatchState.COMPLETED
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(event("r1", 1, done), event("u2", 30), event("r2", 2, done), event("u1", 28)),
            older = "o1",
            newer = "n1",
        )
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("u1", "u2"), state.upcomingIds())
        assertEquals(listOf("r2", "r1"), state.resultIds())
        assertTrue(state.canLoadOlder)
        assertTrue(state.canLoadNewer)
    }

    @Test
    fun `paging merges each page into its section and keeps the other flag`() = runTest(dispatcher) {
        val done = MatchState.COMPLETED
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("r2", 2, done), event("u1", 30)), older = "o1", newer = "n1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("r1", 1, done)), older = null, newer = "x")
        repository.pages[key(setOf(WORLDS), "n1")] = page(listOf(event("u2", 31)), older = "y", newer = null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf("r2", "r1"), vm.uiState.value.resultIds())
        assertFalse(vm.uiState.value.canLoadOlder)
        assertTrue(vm.uiState.value.canLoadNewer)

        vm.loadNewer()
        advanceUntilIdle()
        assertEquals(listOf("u1", "u2"), vm.uiState.value.upcomingIds())
        assertFalse(vm.uiState.value.canLoadNewer)
    }

    @Test
    fun `a match that goes live moves out of upcoming into the live section on the next refresh`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1), event("2", 2)))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("1", "2"), vm.uiState.value.upcomingIds())

        liveRepository.events = listOf(live("1", WORLDS, 1))
        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf("1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(listOf("2"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `a match that ends moves from the live section into the results`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1, MatchState.IN_PROGRESS)))
        liveRepository.events = listOf(live("1", WORLDS, 1))
        val vm = viewModel()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }
        assertEquals(listOf("1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertTrue(vm.uiState.value.resultIds().isEmpty())

        liveRepository.events = emptyList()
        advance(LIVE_SECTION_POLL_INTERVAL)

        assertTrue(vm.uiState.value.liveEvents.isEmpty())
        assertEquals(listOf("1"), vm.uiState.value.resultIds())
        polling.cancel()
    }

    @Test
    fun `the live toggle starts off`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.liveOnly)
    }

    @Test
    fun `the live toggle lists the live matches of every league and keeps the selection`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("w1", WORLDS, 0), live("k1", LCK, 1))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })

        vm.toggleLiveOnly()
        advanceUntilIdle()

        val on = vm.uiState.value
        assertTrue(on.liveOnly)
        assertEquals(listOf("w1", "k1"), on.liveEvents.map { it.matchId })
        assertEquals(setOf(WORLDS), on.selectedLeagueIds)

        vm.toggleLiveOnly()
        advanceUntilIdle()

        val off = vm.uiState.value
        assertFalse(off.liveOnly)
        assertEquals(listOf("w1"), off.liveEvents.map { it.matchId })
        assertEquals(setOf(WORLDS), off.selectedLeagueIds)
        assertEquals(listOf("1"), off.upcomingIds())
    }

    @Test
    fun `turning the live toggle on refreshes the live matches right away without touching the schedule`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, liveRepository.calls)
        assertEquals(1, repository.scheduleCalls)

        liveRepository.events = listOf(live("k1", LCK, 1))
        vm.toggleLiveOnly()
        advanceUntilIdle()

        assertEquals(2, liveRepository.calls)
        assertEquals(listOf("k1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertEquals(1, repository.scheduleCalls)

        vm.toggleLiveOnly()
        advanceUntilIdle()
        assertEquals("turning it off needs no request", 2, liveRepository.calls)
        assertEquals(1, repository.scheduleCalls)
    }

    @Test
    fun `turning the live toggle on while a live request is in flight does not start another`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val gate = CompletableDeferred<Unit>()
        liveRepository.gate = gate
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLiveOnly()
        advanceUntilIdle()
        assertEquals(1, liveRepository.calls)

        liveRepository.events = listOf(live("k1", LCK, 1))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, liveRepository.calls)
        assertEquals(listOf("k1"), vm.uiState.value.liveEvents.map { it.matchId })
    }

    @Test
    fun `with nothing live the live toggle shows an empty list`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLiveOnly()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.liveOnly)
        assertTrue(vm.uiState.value.liveEvents.isEmpty())
    }

    @Test
    fun `changing the league chips while the live toggle is on keeps it on and still loads the selection`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("9", 9)))
        liveRepository.events = listOf(live("k1", LCK, 1))
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleLiveOnly()
        advanceUntilIdle()

        vm.toggleLeague(LCK)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.liveOnly)
        assertEquals(setOf(WORLDS, LCK), state.selectedLeagueIds)
        assertEquals(listOf("k1"), state.liveEvents.map { it.matchId })
        assertEquals(listOf(setOf(WORLDS, LCK)), preferences.savedScheduleLeagueIds)

        vm.toggleLiveOnly()
        advanceUntilIdle()
        assertEquals(listOf("9"), vm.uiState.value.upcomingIds())
        assertEquals(listOf("k1"), vm.uiState.value.liveEvents.map { it.matchId })
    }

    @Test
    fun `polling keeps the live toggle list current`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleLiveOnly()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }
        val before = liveRepository.calls

        liveRepository.events = listOf(live("k1", LCK, 1))
        advance(LIVE_SECTION_POLL_INTERVAL)

        assertEquals(before + 1, liveRepository.calls)
        assertEquals(listOf("k1"), vm.uiState.value.liveEvents.map { it.matchId })
        polling.cancel()
    }

    @Test
    fun `live section keys are unique even when the feed repeats a match`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        liveRepository.events = listOf(live("1", WORLDS, 1), live("1", WORLDS, 1))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        val dayKeys = (state.upcomingDays + state.resultDays).flatMap { it.keys + it.headerKey }
        assertEquals(2, state.liveKeys.size)
        assertEquals(state.liveKeys.size, state.liveKeys.toSet().size)
        assertTrue((state.liveKeys + dayKeys).let { it.size == it.toSet().size })
    }

    @Test
    fun `the live section refreshes every interval while visible and keeps the schedule untouched`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        val vm = viewModel()
        advanceUntilIdle()
        val daysBefore = vm.uiState.value.upcomingDays to vm.uiState.value.resultDays
        val polling = launch { vm.pollWhileVisible() }

        liveRepository.events = listOf(live("w1", WORLDS, 0))
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })

        liveRepository.events = emptyList()
        advance(LIVE_SECTION_POLL_INTERVAL)
        assertTrue(vm.uiState.value.liveEvents.isEmpty())

        assertEquals(3, liveRepository.calls)
        assertEquals(1, repository.scheduleCalls)
        assertEquals(daysBefore, vm.uiState.value.upcomingDays to vm.uiState.value.resultDays)
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

    @Test
    fun `refresh reloads the schedule and the live matches`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        val vm = viewModel()
        advanceUntilIdle()
        val liveBefore = liveRepository.calls

        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1), event("2", 2)), older = "o2")
        liveRepository.events = listOf(live("w1", WORLDS, 3))
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.status)
        assertFalse(state.isRefreshing)
        assertEquals(listOf("1", "2"), state.upcomingIds())
        assertEquals(listOf("w1"), state.liveEvents.map { it.matchId })
        assertEquals(liveBefore + 1, liveRepository.calls)
        assertEquals(listOf(key(setOf(WORLDS), null), key(setOf(WORLDS), null)), repository.requests)
    }

    @Test
    fun `refresh keeps the loaded days on screen while it loads`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        val vm = viewModel()
        advanceUntilIdle()

        repository.gate = CompletableDeferred()
        vm.refresh()
        runCurrent()

        val loading = vm.uiState.value
        assertTrue(loading.isRefreshing)
        assertEquals(LoadStatus.Content, loading.status)
        assertEquals(listOf("1"), loading.upcomingIds())
        assertTrue(loading.canLoadOlder)

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `a failed refresh leaves the schedule as it was`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("0", -30, MatchState.COMPLETED)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadOlder()
        advanceUntilIdle()
        val shown = vm.uiState.value

        repository.scheduleError = IOException("offline")
        liveRepository.error = IOException("offline")
        vm.refresh()
        advanceUntilIdle()

        assertEquals(shown, vm.uiState.value)
        assertEquals(listOf("0"), vm.uiState.value.resultIds())
    }

    @Test
    fun `refresh does not start a live request while a poll is in flight`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        val polling = launch { vm.pollWhileVisible() }
        liveRepository.gate = CompletableDeferred()
        advance(LIVE_SECTION_POLL_INTERVAL)
        val inFlight = liveRepository.calls

        vm.refresh()
        runCurrent()
        assertEquals(inFlight, liveRepository.calls)

        liveRepository.events = listOf(live("w1", WORLDS, 3))
        liveRepository.gate?.complete(Unit)
        liveRepository.gate = null
        advance(LIVE_SECTION_POLL_INTERVAL)

        assertEquals(inFlight + 1, liveRepository.calls)
        assertEquals(listOf("w1"), vm.uiState.value.liveEvents.map { it.matchId })
        assertFalse(vm.uiState.value.isRefreshing)
        polling.cancel()
    }

    @Test
    fun `a second refresh while one is in flight does not request again`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = repository.scheduleCalls

        repository.gate = CompletableDeferred()
        vm.refresh()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(callsBefore + 1, repository.scheduleCalls)

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `no page is loaded while a refresh is in flight and paging works after it`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)), older = "o1")
        repository.pages[key(setOf(WORLDS), "o1")] = page(listOf(event("0", -30, MatchState.COMPLETED)))
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = repository.scheduleCalls

        repository.gate = CompletableDeferred()
        vm.refresh()
        runCurrent()
        vm.loadOlder()
        runCurrent()
        assertEquals(callsBefore + 1, repository.scheduleCalls)

        repository.gate?.complete(Unit)
        repository.gate = null
        advanceUntilIdle()
        vm.loadOlder()
        advanceUntilIdle()

        assertEquals(listOf("0"), vm.uiState.value.resultIds())
        assertEquals(listOf("1"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `changing the leagues during a refresh shows the new selection only`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        repository.pages[key(setOf(WORLDS, LCK), null)] = page(listOf(event("1", 1), event("k", 2)))
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.refresh()
        runCurrent()
        repository.gate = null
        vm.toggleLeague(LCK)
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf("1", "k"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `refresh does nothing while the first load has not shown anything`() = runTest(dispatcher) {
        repository.scheduleError = IOException("boom")
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = repository.scheduleCalls

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LoadStatus.Error)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(callsBefore, repository.scheduleCalls)
    }

    private fun ScheduleUiState.upcomingIds() = upcomingDays.flatMap { d -> d.events.map { it.matchId } }

    private fun ScheduleUiState.resultIds() = resultDays.flatMap { d -> d.events.map { it.matchId } }

    private suspend fun TestScope.advance(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun live(id: String, leagueId: String, hour: Long) =
        event(id, hour, MatchState.IN_PROGRESS).copy(leagueId = leagueId)

    private fun key(ids: Set<String>, token: String?) = ids to token

    private fun page(events: List<ScheduleEvent>, older: String? = null, newer: String? = null) =
        SchedulePage(events, older, newer)

    @Test
    fun `no spoilers is off by default and nothing is saved`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.noSpoilers)
        assertTrue(preferences.savedNoSpoilers.isEmpty())
    }

    @Test
    fun `a stored no spoilers mode is restored`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.noSpoilers)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `toggling no spoilers flips the state and remembers it each time`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleNoSpoilers()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.noSpoilers)

        vm.toggleNoSpoilers()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.noSpoilers)
        assertEquals(listOf(true, false), preferences.savedNoSpoilers)
    }

    @Test
    fun `toggling no spoilers does not reload the schedule or touch the selection`() = runTest(dispatcher) {
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()
        val calls = repository.scheduleCalls

        vm.toggleNoSpoilers()
        advanceUntilIdle()

        assertEquals(calls, repository.scheduleCalls)
        assertEquals(setOf(WORLDS), vm.uiState.value.selectedLeagueIds)
        assertTrue(preferences.savedScheduleLeagueIds.isEmpty())
    }

    @Test
    fun `an unreadable no spoilers preference falls back to off`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        preferences.readFails = true
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.noSpoilers)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `a failed save still flips the no spoilers mode`() = runTest(dispatcher) {
        preferences.writeFails = true
        repository.pages[key(setOf(WORLDS), null)] = page(listOf(event("1", 1)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleNoSpoilers()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.noSpoilers)
    }


    private fun event(id: String, hour: Long, state: MatchState = MatchState.UNSTARTED) = ScheduleEvent(
        startTime = Instant.parse("2026-10-20T00:00:00Z").plusSeconds(hour * 3600),
        state = state,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(id = id, teams = emptyList(), format = null),
    )

    private fun teamEvent(id: String, hour: Long, state: MatchState, vararg names: String, leagueId: String = WORLDS) =
        event(id, hour, state).copy(
            leagueId = leagueId,
            match = Match(id = id, teams = names.map { Team(it, it.take(3), null, null, null) }, format = null),
        )

    private fun favorite(name: String) = FavoriteTeam("id-$name", name.lowercase(), name, name.take(3), null)

    private fun favoritesSetUp() {
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(
                teamEvent("up-t1", 1, MatchState.UNSTARTED, "T1", "Gen.G"),
                teamEvent("up-other", 2, MatchState.UNSTARTED, "KT Rolster", "DRX"),
                teamEvent("res-t1", -5, MatchState.COMPLETED, "Hanwha", "t1"),
                teamEvent("res-other", -6, MatchState.COMPLETED, "KT Rolster", "DRX"),
            ),
        )
        liveRepository.events = listOf(
            teamEvent("live-t1", 0, MatchState.IN_PROGRESS, "T1", "DRX"),
            teamEvent("live-other", 0, MatchState.IN_PROGRESS, "KT Rolster", "DRX"),
            teamEvent("live-lck-t1", 0, MatchState.IN_PROGRESS, "T1", "DK", leagueId = LCK),
        )
    }

    @Test
    fun `without favorites the filter is off and nothing changes`() = runTest(dispatcher) {
        favoritesSetUp()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.favoritesOnly)
        assertEquals(emptyList<String>(), state.favoriteNames)
        assertEquals(listOf("up-t1", "up-other"), state.upcomingIds())
        assertEquals(listOf("res-t1", "res-other"), state.resultIds())
        assertEquals(listOf("live-t1", "live-other"), state.liveEvents.map { it.matchId })
        assertNull(state.favoritesEmptyMessage)
    }

    @Test
    fun `the stored favorites are restored as trimmed names`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite(" T1 "), favorite("Gen.G"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("T1", "Gen.G"), vm.uiState.value.favoriteNames)
        // They do not filter anything until the toggle is on.
        assertEquals(listOf("up-t1", "up-other"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `favorites are read before the first schedule request`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = viewModel()
        advanceUntilIdle()
        // The first request is still in flight and the stored favorites are already applied.
        assertEquals(1, repository.scheduleCalls)
        assertEquals(LoadStatus.Loading, vm.uiState.value.status)
        assertEquals(listOf("T1"), vm.uiState.value.favoriteNames)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `unreadable favorites count as none and the schedule still loads`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        preferences.readFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(emptyList<String>(), vm.uiState.value.favoriteNames)
        assertEquals(listOf("up-t1", "up-other"), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `the favorites toggle keeps only matches of favorites in every section`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("t1"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavoritesOnly()

        val on = vm.uiState.value
        assertTrue(on.favoritesOnly)
        assertEquals(listOf("up-t1"), on.upcomingIds())
        assertEquals(listOf("res-t1"), on.resultIds())
        assertEquals(listOf("live-t1"), on.liveEvents.map { it.matchId })
        assertNull(on.favoritesEmptyMessage)
        // The league selection and the loaded schedule are untouched, and no request was made.
        assertEquals(setOf(WORLDS), on.selectedLeagueIds)
        assertEquals(1, repository.scheduleCalls)

        vm.toggleFavoritesOnly()

        val off = vm.uiState.value
        assertFalse(off.favoritesOnly)
        assertEquals(listOf("up-t1", "up-other"), off.upcomingIds())
        assertEquals(listOf("res-t1", "res-other"), off.resultIds())
        assertEquals(listOf("live-t1", "live-other"), off.liveEvents.map { it.matchId })
        assertEquals(1, repository.scheduleCalls)
    }

    @Test
    fun `with LIVE and favorites both on only live matches of favorites show, of every league`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLiveOnly()
        vm.toggleFavoritesOnly()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.liveOnly)
        assertTrue(state.favoritesOnly)
        assertEquals(listOf("live-t1", "live-lck-t1"), state.liveEvents.map { it.matchId })

        vm.toggleFavoritesOnly()
        assertEquals(listOf("live-t1", "live-other", "live-lck-t1"), vm.uiState.value.liveEvents.map { it.matchId })
    }

    @Test
    fun `when no loaded match is a favorite's, the filter shows an empty message`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("Fnatic"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavoritesOnly()

        val state = vm.uiState.value
        assertEquals(emptyList<String>(), state.upcomingIds())
        assertEquals(emptyList<String>(), state.resultIds())
        assertEquals(emptyList<ScheduleEvent>(), state.liveEvents)
        assertEquals("No matches of your favorites in the loaded range", state.favoritesEmptyMessage)
        assertEquals(LoadStatus.Content, state.status)
    }

    @Test
    fun `with the filter on and no favorites the message says so`() = runTest(dispatcher) {
        favoritesSetUp()
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavoritesOnly()

        assertEquals("No favorite teams yet. Star a team in its profile.", vm.uiState.value.favoritesEmptyMessage)
        assertEquals(emptyList<String>(), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `LIVE and favorites on with no live match of a favorite shows its own message`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("Fnatic"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleLiveOnly()
        vm.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals("No live matches of your favorites right now", vm.uiState.value.favoritesEmptyMessage)
    }

    @Test
    fun `the filter applies to pages loaded later`() = runTest(dispatcher) {
        favoritesSetUp()
        repository.pages[key(setOf(WORLDS), null)] = page(
            listOf(teamEvent("up-t1", 1, MatchState.UNSTARTED, "T1", "Gen.G")),
            older = "o1",
        )
        repository.pages[key(setOf(WORLDS), "o1")] = page(
            listOf(
                teamEvent("old-t1", -9, MatchState.COMPLETED, "T1", "DRX"),
                teamEvent("old-other", -8, MatchState.COMPLETED, "KT Rolster", "DRX"),
            ),
        )
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleFavoritesOnly()

        vm.loadOlder()
        advanceUntilIdle()

        assertEquals(listOf("old-t1"), vm.uiState.value.resultIds())
    }

    @Test
    fun `the favorites toggle is not remembered`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavoritesOnly()
        advanceUntilIdle()

        assertTrue(preferences.savedFavoriteTeams.isEmpty())
        assertTrue(preferences.savedNoSpoilers.isEmpty())
        assertTrue(preferences.savedScheduleLeagueIds.isEmpty())
        assertFalse("a new launch starts with the filter off", viewModel().also { advanceUntilIdle() }.uiState.value.favoritesOnly)
    }

    @Test
    fun `refreshing picks up favorites marked or unmarked meanwhile and re-applies the filter`() = runTest(dispatcher) {
        favoritesSetUp()
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleFavoritesOnly()
        assertEquals(emptyList<String>(), vm.uiState.value.upcomingIds())

        preferences.favoriteTeams = listOf(favorite("T1"))
        vm.refreshFavorites()
        advanceUntilIdle()
        assertEquals(listOf("T1"), vm.uiState.value.favoriteNames)
        assertEquals(listOf("up-t1"), vm.uiState.value.upcomingIds())
        assertNull(vm.uiState.value.favoritesEmptyMessage)

        preferences.favoriteTeams = emptyList()
        vm.refreshFavorites()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.uiState.value.favoriteNames)
        assertEquals(emptyList<String>(), vm.uiState.value.upcomingIds())
    }

    @Test
    fun `a failed refresh keeps the favorites already known`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()

        preferences.readFails = true
        vm.refreshFavorites()
        advanceUntilIdle()

        assertEquals(listOf("T1"), vm.uiState.value.favoriteNames)
    }

    @Test
    fun `reading favorites does not count as a schedule or standings read`() = runTest(dispatcher) {
        favoritesSetUp()
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()
        val readsAfterLoad = preferences.reads

        vm.refreshFavorites()
        advanceUntilIdle()

        assertEquals(readsAfterLoad, preferences.reads)
    }

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
