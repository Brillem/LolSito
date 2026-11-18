package com.example.lolsito.feature.standings.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.StandingsSelection
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.FakeUserPreferences
import com.example.lolsito.testutil.series
import com.example.lolsito.feature.schedule.domain.SchedulePage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StandingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeStandingsRepository()
    private var leaguesError: Throwable? = null
    private var leaguesRequests = 0
    private var leaguesGate: CompletableDeferred<Unit>? = null
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.tournaments[LCK] = listOf(
            tournament(LCK_SUMMER, "2026-06-10", "2026-08-30"),
            tournament(LCK_SPRING, "2026-01-15", "2026-04-20"),
        )
        repository.tournaments[WORLDS] = listOf(
            tournament(WORLDS_2026, "2026-10-15", "2026-11-15"),
            tournament(WORLDS_2025, "2025-10-14", "2025-11-09"),
        )
        listOf(LCK_SUMMER, LCK_SPRING, WORLDS_2026, WORLDS_2025).forEach { repository.standings[it] = standings(it) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val leaguesRepository = LeaguesRepository {
        leaguesRequests++
        leaguesGate?.await()
        leaguesError?.let { throw it }
        listOf(League(LCK, "lck", "LCK", "KOREA", null), League(WORLDS, "worlds", "Worlds", null, null))
    }

    private val preferences = FakeUserPreferences()
    private val schedule = FakeScheduleRepository()

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = StandingsViewModel(
        repository = repository,
        leaguesRepository = leaguesRepository,
        preferences = preferences,
        clock = clock,
        scheduleRepository = schedule,
        savedStateHandle = savedStateHandle,
        defaultLeagueId = LCK,
    )

    @Test
    fun `no spoilers is off by default`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.noSpoilers)
    }

    @Test
    fun `a stored no spoilers mode is read and the rows are the same as with it off`() = runTest(dispatcher) {
        val ranked = StandingsSection("Group", listOf(Ranking(1, Team("T1", "T1", null, null, null), 5, 3)), emptyList())
        val played = StandingsSection(
            "Playoffs",
            emptyList(),
            listOf(SectionMatch(MatchState.COMPLETED, Match("m1", listOf(Team("A", "A", null, null, 2), Team("B", "B", null, null, 0)), null))),
        )
        repository.standings[LCK_SUMMER] = Standings(listOf(StandingsStage("Stage", "stage", listOf(ranked, played))))
        val off = viewModel()
        advanceUntilIdle()
        val rowsOff = off.uiState.value.rows

        preferences.noSpoilers = true
        val on = viewModel()
        advanceUntilIdle()

        assertTrue(on.uiState.value.noSpoilers)
        assertEquals(LoadStatus.Content, on.uiState.value.standingsStatus)
        assertEquals(rowsOff, on.uiState.value.rows)
        assertTrue(on.uiState.value.rows.any { it is StandingsRow.RankingRow })
    }

    @Test
    fun `refreshing picks up a change made in Schedule and a change back`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.noSpoilers)

        preferences.noSpoilers = true
        vm.refreshNoSpoilers()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.noSpoilers)

        preferences.noSpoilers = false
        vm.refreshNoSpoilers()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.noSpoilers)
    }

    @Test
    fun `an unreadable no spoilers preference keeps the current mode`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.noSpoilers)

        preferences.readFails = true
        vm.refreshNoSpoilers()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.noSpoilers)
    }


    private val StandingsUiState.stageNames: List<String>
        get() = rows.filterIsInstance<StandingsRow.StageHeader>().map { it.name }

    @Test
    fun `initial load picks the default tournament and loads its standings`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            val first = awaitItem()
            assertEquals(LoadStatus.Loading, first.tournamentsStatus)
            assertEquals(LCK, first.selectedLeagueId)
            var state = first
            while (state.standingsStatus != LoadStatus.Content) state = awaitItem()
            assertEquals(LoadStatus.Content, state.tournamentsStatus)
            assertEquals(2, state.leagues.size)
            assertEquals(listOf(LCK_SUMMER, LCK_SPRING), state.tournaments.map { it.id })
            assertEquals(LCK_SUMMER, state.selectedTournamentId)
            assertEquals(listOf(LCK_SUMMER), state.stageNames)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `changing the league reloads tournaments and resets the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(WORLDS)
        val loading = vm.uiState.value
        assertEquals(WORLDS, loading.selectedLeagueId)
        assertEquals(LoadStatus.Loading, loading.tournamentsStatus)
        assertTrue(loading.tournaments.isEmpty())
        assertNull(loading.selectedTournamentId)
        assertTrue(loading.rows.isEmpty())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(WORLDS_2026, WORLDS_2025), state.tournaments.map { it.id })
        assertEquals(WORLDS_2026, state.selectedTournamentId)
        assertEquals(listOf(WORLDS_2026), state.stageNames)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER", "t:$WORLDS", "s:$WORLDS_2026"), repository.requests)
    }

    @Test
    fun `changing the tournament reloads the standings`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectTournament(LCK_SPRING)
        assertEquals(LCK_SPRING, vm.uiState.value.selectedTournamentId)
        assertEquals(LoadStatus.Loading, vm.uiState.value.standingsStatus)
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(listOf(LCK_SPRING), vm.uiState.value.stageNames)
        assertEquals("s:$LCK_SPRING", repository.requests.last())
    }

    @Test
    fun `selecting the current league or tournament again does not reload`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = repository.requests.size

        vm.selectLeague(LCK)
        vm.selectTournament(LCK_SUMMER)
        advanceUntilIdle()

        assertEquals(before, repository.requests.size)
    }

    @Test
    fun `tournaments error then retry recovers`() = runTest(dispatcher) {
        repository.tournamentsError = IOException("tournaments down")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LoadStatus.Error("No connection. Check your internet and try again."), vm.uiState.value.tournamentsStatus)

        repository.tournamentsError = null
        vm.retryTournaments()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.tournamentsStatus)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
    }

    @Test
    fun `leagues error does not block tournaments and standings`() = runTest(dispatcher) {
        leaguesError = IOException("leagues down")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Error("No connection. Check your internet and try again."), state.leaguesStatus)
        assertTrue(state.leagues.isEmpty())
        assertEquals("a neutral label while the leagues are unavailable", "League", state.selectedLeagueLabel)
        assertEquals(LoadStatus.Content, state.tournamentsStatus)
        assertEquals(LCK_SUMMER, state.selectedTournamentId)
        assertEquals(LoadStatus.Content, state.standingsStatus)
        assertEquals(listOf(LCK_SUMMER), state.stageNames)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `leagues retry populates the picker without reloading the standings`() = runTest(dispatcher) {
        leaguesError = IOException("leagues down")
        val vm = viewModel()
        advanceUntilIdle()
        val requestsBefore = repository.requests.toList()

        leaguesError = null
        vm.retryLeagues()
        assertEquals(LoadStatus.Loading, vm.uiState.value.leaguesStatus)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.leaguesStatus)
        assertEquals(2, state.leagues.size)
        assertEquals("LCK", state.selectedLeagueLabel)
        assertEquals(LoadStatus.Content, state.standingsStatus)
        assertEquals(listOf(LCK_SUMMER), state.stageNames)
        assertEquals(requestsBefore, repository.requests)
        assertEquals(2, leaguesRequests)
    }

    @Test
    fun `tournaments error with leagues loaded shows the tournaments error and retry recovers`() = runTest(dispatcher) {
        repository.tournamentsError = IOException("tournaments down")
        val vm = viewModel()
        advanceUntilIdle()
        val failed = vm.uiState.value
        assertEquals(LoadStatus.Content, failed.leaguesStatus)
        assertEquals(2, failed.leagues.size)
        assertEquals(LoadStatus.Error("No connection. Check your internet and try again."), failed.tournamentsStatus)

        repository.tournamentsError = null
        vm.retryTournaments()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.tournamentsStatus)
        assertEquals(LoadStatus.Content, state.standingsStatus)
        assertEquals(LCK_SUMMER, state.selectedTournamentId)
        assertEquals("leagues are not reloaded by a tournaments retry", 1, leaguesRequests)
    }

    @Test
    fun `standings error then retry recovers`() = runTest(dispatcher) {
        repository.standingsError = IOException("standings down")
        val vm = viewModel()
        advanceUntilIdle()
        val failed = vm.uiState.value
        assertEquals(LoadStatus.Content, failed.tournamentsStatus)
        assertEquals(LoadStatus.Error("No connection. Check your internet and try again."), failed.standingsStatus)
        assertEquals(LCK_SUMMER, failed.selectedTournamentId)

        repository.standingsError = null
        vm.retryStandings()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(listOf(LCK_SUMMER), vm.uiState.value.stageNames)
    }

    @Test
    fun `empty tournaments and empty standings show empty states`() = runTest(dispatcher) {
        repository.tournaments[LCK] = emptyList()
        repository.standings[WORLDS_2026] = Standings(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LoadStatus.Empty, vm.uiState.value.tournamentsStatus)
        assertEquals(listOf("t:$LCK"), repository.requests)

        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        assertEquals(LoadStatus.Content, vm.uiState.value.tournamentsStatus)
        assertEquals(LoadStatus.Empty, vm.uiState.value.standingsStatus)
    }

    @Test
    fun `stale standings from a previous tournament do not overwrite newer ones`() = runTest(dispatcher) {
        val slowSummer = CompletableDeferred<Unit>()
        repository.standingsGates[LCK_SUMMER] = slowSummer
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LoadStatus.Loading, vm.uiState.value.standingsStatus)

        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        slowSummer.complete(Unit)
        advanceUntilIdle()

        assertEquals(LCK_SPRING, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf(LCK_SPRING), vm.uiState.value.stageNames)
    }

    @Test
    fun `stale tournaments from a previous league do not overwrite newer ones`() = runTest(dispatcher) {
        val slowLck = CompletableDeferred<Unit>()
        repository.tournamentGates[LCK] = slowLck
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        slowLck.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WORLDS, state.selectedLeagueId)
        assertEquals(listOf(WORLDS_2026, WORLDS_2025), state.tournaments.map { it.id })
        assertEquals(WORLDS_2026, state.selectedTournamentId)
        assertEquals(listOf(WORLDS_2026), state.stageNames)
    }

    @Test
    fun `stale standings from a previous league do not overwrite newer ones`() = runTest(dispatcher) {
        val slowSummer = CompletableDeferred<Unit>()
        repository.standingsGates[LCK_SUMMER] = slowSummer
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        slowSummer.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(WORLDS_2026), vm.uiState.value.stageNames)
    }

    @Test
    fun `selection is written to the saved state handle`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        advanceUntilIdle()
        assertEquals(LCK, handle.get<String>(StandingsViewModel.KEY_LEAGUE_ID))
        assertEquals(LCK_SUMMER, handle.get<String>(StandingsViewModel.KEY_TOURNAMENT_ID))

        vm.selectTournament(LCK_SPRING)
        assertEquals(LCK_SPRING, handle.get<String>(StandingsViewModel.KEY_TOURNAMENT_ID))

        vm.selectLeague(WORLDS)
        assertEquals(WORLDS, handle.get<String>(StandingsViewModel.KEY_LEAGUE_ID))
        assertNull("a league change drops the old tournament", handle.get<String>(StandingsViewModel.KEY_TOURNAMENT_ID))
        advanceUntilIdle()
        assertEquals(WORLDS_2026, handle.get<String>(StandingsViewModel.KEY_TOURNAMENT_ID))
    }

    @Test
    fun `restored league and tournament are used on init`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(StandingsViewModel.KEY_LEAGUE_ID to WORLDS, StandingsViewModel.KEY_TOURNAMENT_ID to WORLDS_2025)
        )
        val vm = viewModel(handle)
        assertEquals(WORLDS, vm.uiState.value.selectedLeagueId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WORLDS_2025, state.selectedTournamentId)
        assertEquals(listOf(WORLDS_2025), state.stageNames)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2025"), repository.requests)
    }

    @Test
    fun `restored tournament missing from the list falls back to the default`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(StandingsViewModel.KEY_LEAGUE_ID to LCK, StandingsViewModel.KEY_TOURNAMENT_ID to "gone")
        )
        val vm = viewModel(handle)
        advanceUntilIdle()

        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
        assertEquals(LCK_SUMMER, handle.get<String>(StandingsViewModel.KEY_TOURNAMENT_ID))
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `nothing stored keeps the default selection and saves nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LCK, vm.uiState.value.selectedLeagueId)
        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
        assertTrue(preferences.savedStandingsSelections.isEmpty())
    }

    @Test
    fun `stored league and tournament are restored without loading the default first`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WORLDS, state.selectedLeagueId)
        assertEquals("Worlds", state.selectedLeagueLabel)
        assertEquals(WORLDS_2025, state.selectedTournamentId)
        assertEquals(listOf(WORLDS_2025), state.stageNames)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2025"), repository.requests)
        assertTrue("restoring does not write back", preferences.savedStandingsSelections.isEmpty())
    }

    @Test
    fun `the stored league is never replaced by the default while it loads`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.standingsStatus != LoadStatus.Content) {
                if (state.selectedTournamentId != null) assertEquals(WORLDS, state.selectedLeagueId)
                state = awaitItem()
            }
            assertEquals(WORLDS, state.selectedLeagueId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored tournament missing from the list falls back to the default of the stored league`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, "gone")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WORLDS, vm.uiState.value.selectedLeagueId)
        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2026"), repository.requests)
    }

    @Test
    fun `a stored league without a tournament uses the default tournament`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2026"), repository.requests)
    }

    @Test
    fun `a stored league that is no longer listed falls back to the default selection`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection("gone-league", "gone-tournament")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LCK, vm.uiState.value.selectedLeagueId)
        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `a stored league is trusted when the leagues cannot be loaded`() = runTest(dispatcher) {
        leaguesError = IOException("leagues down")
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WORLDS_2025, vm.uiState.value.selectedTournamentId)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(LoadStatus.Error("No connection. Check your internet and try again."), vm.uiState.value.leaguesStatus)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2025"), repository.requests)
    }

    @Test
    fun `the saved state handle wins over the stored selection`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val handle = SavedStateHandle(
            mapOf(StandingsViewModel.KEY_LEAGUE_ID to LCK, StandingsViewModel.KEY_TOURNAMENT_ID to LCK_SPRING)
        )
        val vm = viewModel(handle)
        advanceUntilIdle()

        assertEquals(LCK, vm.uiState.value.selectedLeagueId)
        assertEquals(LCK_SPRING, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$LCK", "s:$LCK_SPRING"), repository.requests)
        assertEquals("the stored selection is not even read", 0, preferences.reads)
    }

    @Test
    fun `picking a tournament and a league saves the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        vm.selectTournament(WORLDS_2025)
        advanceUntilIdle()

        assertEquals(
            listOf(
                StandingsSelection(LCK, LCK_SPRING),
                StandingsSelection(WORLDS, null),
                StandingsSelection(WORLDS, WORLDS_2025),
            ),
            preferences.savedStandingsSelections,
        )
    }

    @Test
    fun `selecting the current league or tournament again saves nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(LCK)
        vm.selectTournament(LCK_SUMMER)
        advanceUntilIdle()

        assertTrue(preferences.savedStandingsSelections.isEmpty())
    }

    @Test
    fun `a failure reading preferences falls back to the default selection`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        preferences.readFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LCK, vm.uiState.value.selectedLeagueId)
        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `a failure writing preferences does not stop the selection from loading`() = runTest(dispatcher) {
        preferences.writeFails = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        assertEquals(listOf(LCK_SPRING), vm.uiState.value.stageNames)

        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertTrue(preferences.savedStandingsSelections.isEmpty())
    }

    @Test
    fun `a league picked while the stored selection is being restored wins and is saved`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(LCK, LCK_SPRING)
        val gate = CompletableDeferred<Unit>()
        leaguesGate = gate
        val vm = viewModel()
        runCurrent()

        vm.selectLeague(WORLDS)
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WORLDS, state.selectedLeagueId)
        assertEquals(WORLDS_2026, state.selectedTournamentId)
        assertEquals(listOf(WORLDS_2026), state.stageNames)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2026"), repository.requests)
        assertEquals(listOf(StandingsSelection(WORLDS, null)), preferences.savedStandingsSelections)
        assertEquals("the leagues are requested once", 1, leaguesRequests)
    }

    @Test
    fun `a tournament picked while the stored selection is being restored wins and is saved`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val gate = CompletableDeferred<Unit>()
        leaguesGate = gate
        val vm = viewModel()
        runCurrent()

        vm.selectTournament(LCK_SPRING)
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LCK, state.selectedLeagueId)
        assertEquals(LCK_SPRING, state.selectedTournamentId)
        assertEquals(listOf(LCK_SPRING), state.stageNames)
        assertEquals(listOf("t:$LCK", "s:$LCK_SPRING"), repository.requests)
        assertEquals(listOf(StandingsSelection(LCK, LCK_SPRING)), preferences.savedStandingsSelections)
    }

    @Test
    fun `a selection made before init resumes loads the tournaments once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectLeague(WORLDS)
        advanceUntilIdle()

        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2026"), repository.requests)
        assertEquals(1, leaguesRequests)
    }

    @Test
    fun `the leagues are requested once even when a stored selection is validated`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.leagues.size)
        assertEquals(1, leaguesRequests)
    }

    @Test
    fun `a hanging leagues request delays the stored selection only up to the validation timeout`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(WORLDS, WORLDS_2025)
        leaguesGate = CompletableDeferred()
        val vm = viewModel()
        advanceTimeBy(StandingsViewModel.LEAGUES_VALIDATION_TIMEOUT.inWholeMilliseconds - 1)
        assertTrue("nothing is requested while the stored league is being validated", repository.requests.isEmpty())

        advanceTimeBy(2)
        runCurrent()

        val state = vm.uiState.value
        assertEquals(WORLDS, state.selectedLeagueId)
        assertEquals(WORLDS_2025, state.selectedTournamentId)
        assertEquals(LoadStatus.Content, state.standingsStatus)
        assertEquals(LoadStatus.Loading, state.leaguesStatus)
        assertEquals(listOf("t:$WORLDS", "s:$WORLDS_2025"), repository.requests)
        assertEquals(1, leaguesRequests)
    }

    @Test
    fun `stored favorites are read as names and leave the rows and the no spoilers mode alone`() = runTest(dispatcher) {
        val plain = viewModel()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), plain.uiState.value.favoriteNames)

        preferences.noSpoilers = true
        preferences.favoriteTeams = listOf(favorite(" T1 "), favorite("Gen.G"), favorite("t1"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("T1", "Gen.G"), vm.uiState.value.favoriteNames)
        assertTrue(vm.uiState.value.noSpoilers)
        assertEquals(plain.uiState.value.rows, vm.uiState.value.rows)
    }

    @Test
    fun `favorites are read again when the screen comes back`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        preferences.favoriteTeams = listOf(favorite("T1"))
        vm.refreshFavorites()
        advanceUntilIdle()

        assertEquals(listOf("T1"), vm.uiState.value.favoriteNames)
    }

    @Test
    fun `unreadable favorites keep the ones already shown`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(favorite("T1"))
        val vm = viewModel()
        advanceUntilIdle()

        preferences.readFails = true
        vm.refreshFavorites()
        advanceUntilIdle()

        assertEquals(listOf("T1"), vm.uiState.value.favoriteNames)
    }

    @Test
    fun `refresh reloads the standings of the selected tournament`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(LCK_SUMMER), vm.uiState.value.stageNames)

        repository.standings[LCK_SUMMER] = standings("updated")
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("updated"), vm.uiState.value.stageNames)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(listOf("t:$LCK", "s:$LCK_SUMMER", "s:$LCK_SUMMER"), repository.requests)
    }

    @Test
    fun `refresh asks for fresh standings past any cache`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repository.refreshes)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(LCK_SUMMER), repository.refreshes)
    }

    @Test
    fun `refresh keeps the rows on screen while it loads`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val shown = vm.uiState.value.rows

        repository.standings[LCK_SUMMER] = standings("updated")
        repository.standingsGates[LCK_SUMMER] = CompletableDeferred()
        vm.refresh()
        runCurrent()

        assertTrue(vm.uiState.value.isRefreshing)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(shown, vm.uiState.value.rows)

        repository.standingsGates.getValue(LCK_SUMMER).complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(listOf("updated"), vm.uiState.value.stageNames)
    }

    @Test
    fun `a failed refresh leaves the standings as they were`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val shown = vm.uiState.value.rows

        repository.standingsError = IOException("offline")
        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
        assertEquals(shown, vm.uiState.value.rows)
    }

    @Test
    fun `picking another tournament during a refresh shows that tournament only`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repository.standingsGates[LCK_SUMMER] = CompletableDeferred()
        vm.refresh()
        runCurrent()

        vm.selectTournament(LCK_SPRING)
        repository.standingsGates.getValue(LCK_SUMMER).complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(listOf(LCK_SPRING), vm.uiState.value.stageNames)
    }

    @Test
    fun `retrying the standings still shows them loading without the old rows`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repository.standingsGates[LCK_SUMMER] = CompletableDeferred()
        vm.retryStandings()
        runCurrent()

        assertEquals(LoadStatus.Loading, vm.uiState.value.standingsStatus)
        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.isRefreshing)
        repository.standingsGates.getValue(LCK_SUMMER).complete(Unit)
        advanceUntilIdle()
        assertEquals(LoadStatus.Content, vm.uiState.value.standingsStatus)
    }

    @Test
    fun `opening a tournament of another league shows it and saves it like a manual pick`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openTournament(WORLDS, WORLDS_2025)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WORLDS, state.selectedLeagueId)
        assertEquals(WORLDS_2025, state.selectedTournamentId)
        assertEquals(LoadStatus.Content, state.standingsStatus)
        assertEquals(StandingsSelection(WORLDS, WORLDS_2025), preferences.savedStandingsSelections.last())
    }

    @Test
    fun `opening a tournament of the same league changes only the tournament`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openTournament(LCK, LCK_SPRING)
        advanceUntilIdle()

        assertEquals(LCK, vm.uiState.value.selectedLeagueId)
        assertEquals(LCK_SPRING, vm.uiState.value.selectedTournamentId)
        assertEquals(StandingsSelection(LCK, LCK_SPRING), preferences.savedStandingsSelections.last())
    }

    @Test
    fun `opening the tournament already shown requests and saves nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val requests = repository.requests.size

        vm.openTournament(LCK, LCK_SUMMER)
        advanceUntilIdle()

        assertEquals(requests, repository.requests.size)
        assertTrue(preferences.savedStandingsSelections.isEmpty())
    }

    @Test
    fun `a tournament opened on a fresh screen wins over the stored selection`() = runTest(dispatcher) {
        preferences.standingsSelection = StandingsSelection(LCK, LCK_SPRING)
        val vm = viewModel()

        vm.openTournament(WORLDS, WORLDS_2026)
        advanceUntilIdle()

        assertEquals(WORLDS, vm.uiState.value.selectedLeagueId)
        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
        assertEquals(StandingsSelection(WORLDS, WORLDS_2026), preferences.savedStandingsSelections.last())
        assertEquals(1, repository.requests.count { it.startsWith("t:") })
    }

    @Test
    fun `opening a tournament missing from its league falls back to the default one`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openTournament(WORLDS, "gone")
        advanceUntilIdle()

        assertEquals(WORLDS_2026, vm.uiState.value.selectedTournamentId)
    }

    // Stages, Table | Matches and the team filter

    private fun rankedTeam(name: String) = Team(name, name.take(3).uppercase(), "logo-$name", null, null, id = "id-$name")

    /** A played regular season (Alpha 5-0, Beta 4-1, Gamma 0-5) and a playoff final not played yet. */
    private fun stagedStandings() = Standings(
        listOf(
            StandingsStage(
                "Regular Season", "regular_season",
                listOf(
                    StandingsSection(
                        "Regular Season",
                        listOf(Ranking(1, rankedTeam("Alpha"), 5, 0), Ranking(2, rankedTeam("Beta"), 4, 1), Ranking(3, rankedTeam("Gamma"), 0, 5)),
                        emptyList(),
                    ),
                ),
            ),
            StandingsStage(
                "Playoffs", "playoffs",
                listOf(
                    StandingsSection(
                        "Playoffs",
                        emptyList(),
                        listOf(SectionMatch(MatchState.UNSTARTED, Match("final", listOf(rankedTeam("Alpha"), rankedTeam("Beta")), null))),
                    ),
                ),
            ),
        ),
    )

    private fun event(id: String, date: String, first: String, second: String) =
        series(id, 1, first, second).copy(startTime = Instant.parse("${date}T12:00:00Z"))

    /** LCK Summer runs 2026-06-10..2026-08-30: m1 and m2 are regular season, "final" the playoff, "old" before it. */
    private fun stagedSchedule() {
        schedule.pages[null] = SchedulePage(
            listOf(
                event("old", "2026-03-01", "Alpha", "Gamma"),
                event("m1", "2026-06-12", "Alpha", "Beta"),
                event("m2", "2026-06-13", "Beta", "Gamma"),
                event("final", "2026-08-29", "Alpha", "Beta"),
            ),
            olderToken = null,
            newerToken = null,
        )
    }

    private val StandingsUiState.matchIds: List<String?>
        get() = matchDays.flatMap { day -> day.events.map { it.matchId } }

    private val StandingsUiState.stageKey: (String) -> String
        get() = { name -> stageTabs.first { it.name == name }.key }

    @Test
    fun `the default stage is shown alone, with a tab per stage`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("Regular Season", "Playoffs"), state.stageTabs.map { it.name })
        assertEquals(state.stageKey("Regular Season"), state.selectedStageKey)
        assertEquals(listOf("Regular Season"), state.stageNames)
    }

    @Test
    fun `a selected stage shows its rows and survives a new view model on the same saved state`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        advanceUntilIdle()

        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))

        assertEquals(listOf("Playoffs"), vm.uiState.value.stageNames)
        val restored = viewModel(handle)
        advanceUntilIdle()
        assertEquals(listOf("Playoffs"), restored.uiState.value.stageNames)
    }

    @Test
    fun `the selected stage resets when the tournament or the league changes`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        repository.standings[LCK_SPRING] = stagedStandings()
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))
        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        assertEquals(listOf("Regular Season"), vm.uiState.value.stageNames)

        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))
        vm.selectLeague(WORLDS)
        advanceUntilIdle()
        vm.selectLeague(LCK)
        advanceUntilIdle()
        assertEquals(LCK_SUMMER, vm.uiState.value.selectedTournamentId)
        assertEquals(listOf("Regular Season"), vm.uiState.value.stageNames)
    }

    @Test
    fun `a refresh keeps the selected stage`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(LCK_SUMMER), repository.refreshes)
        assertEquals(listOf("Playoffs"), vm.uiState.value.stageNames)
    }

    @Test
    fun `matches are offered for a ranked stage of a tournament with dates only`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShowMatches)

        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))
        assertFalse(vm.uiState.value.canShowMatches)

        repository.tournaments[LCK] = listOf(Tournament(LCK_SUMMER, LCK_SUMMER, LocalDate.parse("2026-06-10"), null))
        val undated = viewModel()
        advanceUntilIdle()
        assertFalse(undated.uiState.value.canShowMatches)
    }

    @Test
    fun `matches load only when first opened, keep the regular stage ones and are kept per tournament`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        stagedSchedule()
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(schedule.requests.isEmpty())
        assertEquals(StageView.TABLE, vm.uiState.value.stageView)

        vm.selectStageView(StageView.MATCHES)
        assertEquals(LoadStatus.Loading, vm.uiState.value.matchesStatus)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StageView.MATCHES, state.stageView)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertEquals(listOf<String?>("m1", "m2"), state.matchIds)
        assertEquals(listOf(LocalDate.parse("2026-06-12"), LocalDate.parse("2026-06-13")), state.matchDays.map { it.date })
        assertEquals(listOf(listOf(LCK) to null), schedule.requests)

        vm.selectStageView(StageView.TABLE)
        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        assertEquals(1, schedule.requests.size)
        assertEquals(listOf<String?>("m1", "m2"), vm.uiState.value.matchIds)
    }

    @Test
    fun `a matches failure shows an error and retry loads them`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        stagedSchedule()
        schedule.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.matchesStatus is LoadStatus.Error)

        schedule.error = null
        vm.retryMatches()
        advanceUntilIdle()
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
        assertEquals(listOf<String?>("m1", "m2"), vm.uiState.value.matchIds)
    }

    @Test
    fun `a tournament change goes back to the table and loads its matches again when opened`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        repository.standings[LCK_SPRING] = stagedStandings()
        stagedSchedule()
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()

        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        assertEquals(StageView.TABLE, vm.uiState.value.stageView)

        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        assertEquals(2, schedule.requests.size)
        // LCK Spring runs 2026-01-15..2026-04-20: only "old" (March) falls in it.
        assertEquals(listOf<String?>("old"), vm.uiState.value.matchIds)
    }

    @Test
    fun `the team filter keeps that team's matches, shows its standing and lists favorites first`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        stagedSchedule()
        preferences.favoriteTeams = listOf(favorite("Gamma"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        assertEquals(listOf("Gamma", "Alpha", "Beta"), vm.uiState.value.teamOptions.map { it.team.name })

        vm.selectTeamFilter(rankedTeam("Gamma"))

        assertEquals(listOf<String?>("m2"), vm.uiState.value.matchIds)
        assertEquals("3rd · 0-5", vm.uiState.value.teamFilterStanding)
        assertEquals(1, schedule.requests.size)

        vm.selectTeamFilter(null)
        assertEquals(listOf<String?>("m1", "m2"), vm.uiState.value.matchIds)
        assertNull(vm.uiState.value.teamFilterStanding)
    }

    @Test
    fun `the team filter survives the table and other stages, and resets with the tournament`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        repository.standings[LCK_SPRING] = stagedStandings()
        stagedSchedule()
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        vm.selectTeamFilter(rankedTeam("Beta"))

        vm.selectStageView(StageView.TABLE)
        vm.selectStage(vm.uiState.value.stageKey("Playoffs"))
        assertNull(vm.uiState.value.teamFilterStanding)
        vm.selectStage(vm.uiState.value.stageKey("Regular Season"))
        vm.selectStageView(StageView.MATCHES)

        assertEquals(rankedTeam("Beta"), vm.uiState.value.teamFilter)
        assertEquals("2nd · 4-1", vm.uiState.value.teamFilterStanding)
        assertEquals(listOf<String?>("m1", "m2"), vm.uiState.value.matchIds)

        vm.selectTournament(LCK_SPRING)
        advanceUntilIdle()
        assertNull(vm.uiState.value.teamFilter)
    }

    @Test
    fun `a refresh reloads the matches once they were loaded, and only then`() = runTest(dispatcher) {
        repository.standings[LCK_SUMMER] = stagedStandings()
        stagedSchedule()
        val vm = viewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertTrue(schedule.requests.isEmpty())

        vm.selectStageView(StageView.MATCHES)
        advanceUntilIdle()
        schedule.pages[null] = SchedulePage(listOf(event("m3", "2026-06-20", "Alpha", "Gamma")), null, null)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, schedule.requests.size)
        assertEquals(listOf<String?>("m3"), vm.uiState.value.matchIds)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    private fun favorite(name: String) = FavoriteTeam("id-$name", name.trim().lowercase(), name, name.trim().take(3), null)

    private fun tournament(id: String, start: String, end: String) =
        Tournament(id, id, LocalDate.parse(start), LocalDate.parse(end))

    /** Each tournament's standings hold one stage named after the tournament id. */
    private fun standings(tournamentId: String) =
        Standings(listOf(StandingsStage(tournamentId, tournamentId, emptyList())))

    private class FakeStandingsRepository : StandingsRepository {
        val tournaments = mutableMapOf<String, List<Tournament>>()
        val standings = mutableMapOf<String, Standings>()
        val tournamentGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val standingsGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val requests = mutableListOf<String>()
        val refreshes = mutableListOf<String>()
        var tournamentsError: Throwable? = null
        var standingsError: Throwable? = null

        override suspend fun getTournaments(leagueId: String): List<Tournament> {
            tournamentGates[leagueId]?.await()
            tournamentsError?.let { throw it }
            requests += "t:$leagueId"
            return tournaments[leagueId] ?: error("No tournaments for $leagueId")
        }

        override suspend fun getStandings(tournamentId: String): Standings {
            standingsGates[tournamentId]?.await()
            standingsError?.let { throw it }
            requests += "s:$tournamentId"
            return standings[tournamentId] ?: error("No standings for $tournamentId")
        }

        override suspend fun refreshStandings(tournamentId: String): Standings {
            refreshes += tournamentId
            return getStandings(tournamentId)
        }
    }

    private companion object {
        const val LCK = "98767991310872058"
        const val WORLDS = "98767975604431411"
        const val LCK_SUMMER = "lck-summer"
        const val LCK_SPRING = "lck-spring"
        const val WORLDS_2026 = "worlds-2026"
        const val WORLDS_2025 = "worlds-2025"
    }
}
