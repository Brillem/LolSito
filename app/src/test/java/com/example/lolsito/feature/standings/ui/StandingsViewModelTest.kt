package com.example.lolsito.feature.standings.ui

import app.cash.turbine.test
import com.example.lolsito.core.model.League
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
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

    private fun viewModel() = StandingsViewModel(
        repository = repository,
        loadLeagues = {
            leaguesRequests++
            leaguesError?.let { throw it }
            listOf(League(LCK, "lck", "LCK", "KOREA", null), League(WORLDS, "worlds", "Worlds", null, null))
        },
        clock = clock,
        defaultLeagueId = LCK,
    )

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
        assertEquals(LoadStatus.Error("tournaments down"), vm.uiState.value.tournamentsStatus)

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
        assertEquals(LoadStatus.Error("leagues down"), state.leaguesStatus)
        assertTrue(state.leagues.isEmpty())
        assertEquals("LCK", state.selectedLeagueLabel)
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
        assertEquals(LoadStatus.Error("tournaments down"), failed.tournamentsStatus)

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
        assertEquals(LoadStatus.Error("standings down"), failed.standingsStatus)
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
