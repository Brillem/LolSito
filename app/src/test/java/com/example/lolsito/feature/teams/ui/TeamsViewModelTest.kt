package com.example.lolsito.feature.teams.ui

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.testutil.FakeReminderScheduler
import com.example.lolsito.testutil.FakeReminderSyncTrigger
import com.example.lolsito.testutil.FakeUserPreferences
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    private val repository = FakeStandingsRepository()
    private val preferences = FakeUserPreferences()
    private var leaguesError: Throwable? = null
    private val leaguesRepository = LeaguesRepository {
        leaguesError?.let { throw it }
        listOf(League(LCK, "lck", "LCK", "KOREA", null), League(LEC, "lec", "LEC", "EMEA", null))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.tournaments[LCK] = listOf(Tournament("lck-now", "lck", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01")))
        repository.tournaments[LEC] = listOf(Tournament("lec-now", "lec", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01")))
        repository.standings["lck-now"] = standingsOf(team("T1", "T1", "1"), team("Gen.G", "GEN", "2"))
        repository.standings["lec-now"] = standingsOf(team("G2 Esports", "G2", "3"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val reminderScheduler = FakeReminderScheduler()
    private val reminderSync = FakeReminderSyncTrigger()

    private fun viewModel() =
        TeamsViewModel(repository, leaguesRepository, preferences, clock, reminderScheduler, reminderSync, defaultLeagueId = LCK)

    @Test
    fun `loads the leagues and the default league's current teams`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(LoadStatus.Loading, vm.uiState.value.status)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.leaguesStatus)
        assertEquals(listOf(LCK, LEC), state.leagues.map { it.id })
        assertEquals(LCK, state.selectedLeagueId)
        assertEquals("LCK", state.selectedLeagueName)
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(listOf(TeamSummary("2", "Gen.G", "GEN", null), TeamSummary("1", "T1", "T1", null)), state.teams)
        assertEquals(listOf("lck-now"), repository.standingsRequests)
    }

    @Test
    fun `picking another league shows its teams`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(LEC)
        assertEquals(LoadStatus.Loading, vm.uiState.value.status)
        advanceUntilIdle()

        assertEquals(LEC, vm.uiState.value.selectedLeagueId)
        assertEquals(listOf("G2 Esports"), vm.uiState.value.teams.map { it.name })
        assertEquals(listOf("lck-now", "lec-now"), repository.standingsRequests)
    }

    @Test
    fun `picking the selected league again sends no request`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(LCK)
        advanceUntilIdle()

        assertEquals(listOf("lck-now"), repository.standingsRequests)
    }

    @Test
    fun `a league without current teams is empty`() = runTest(dispatcher) {
        repository.tournaments[LCK] = emptyList()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Empty, vm.uiState.value.status)
        assertEquals(emptyList<TeamSummary>(), vm.uiState.value.teams)
    }

    @Test
    fun `a failed load is an error and retry loads again`() = runTest(dispatcher) {
        repository.error = IOException("offline")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Error("offline"), vm.uiState.value.status)
        assertEquals(emptyList<TeamSummary>(), vm.uiState.value.teams)

        repository.error = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf("Gen.G", "T1"), vm.uiState.value.teams.map { it.name })
    }

    private val t1 = FavoriteTeam("1", "t1", "T1", "T1", "https://img/t1.png")
    private val g2 = FavoriteTeam("3", "g2-esports", "G2 Esports", "G2", null)

    @Test
    fun `the stored favorites are listed, in stored order, whatever league is selected`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(g2, t1)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(g2, t1), vm.uiState.value.favorites)
        // G2 is not in the selected league (LCK) and is listed anyway; no extra request was needed.
        assertEquals(listOf("lck-now"), repository.standingsRequests)
        vm.selectLeague(LEC)
        advanceUntilIdle()
        assertEquals(listOf(g2, t1), vm.uiState.value.favorites)
    }

    @Test
    fun `with no stored favorites the list is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList<FavoriteTeam>(), vm.uiState.value.favorites)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
    }

    @Test
    fun `unreadable favorites leave the list empty and do not block the teams`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(t1)
        preferences.readFails = true

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList<FavoriteTeam>(), vm.uiState.value.favorites)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(listOf("Gen.G", "T1"), vm.uiState.value.teams.map { it.name })
    }

    @Test
    fun `refreshing shows a favorite marked or unmarked meanwhile`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        preferences.favoriteTeams = listOf(t1)
        vm.refreshFavorites()
        advanceUntilIdle()
        assertEquals(listOf(t1), vm.uiState.value.favorites)

        preferences.favoriteTeams = emptyList()
        vm.refreshFavorites()
        advanceUntilIdle()
        assertEquals(emptyList<FavoriteTeam>(), vm.uiState.value.favorites)
    }

    @Test
    fun `a failed refresh keeps the favorites already shown`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(t1)
        val vm = viewModel()
        advanceUntilIdle()

        preferences.readFails = true
        vm.refreshFavorites()
        advanceUntilIdle()

        assertEquals(listOf(t1), vm.uiState.value.favorites)
    }

    @Test
    fun `changing the league or retrying does not touch the favorites`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(t1)
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectLeague(LEC)
        vm.retry()
        assertEquals(listOf(t1), vm.uiState.value.favorites)
        advanceUntilIdle()
        assertEquals(listOf(t1), vm.uiState.value.favorites)
    }

    @Test
    fun `failed leagues do not block the teams and retry loads them again`() = runTest(dispatcher) {
        leaguesError = IOException("no leagues")

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.leaguesStatus is LoadStatus.Error)
        assertEquals(emptyList<League>(), vm.uiState.value.leagues)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals("League", vm.uiState.value.selectedLeagueName)

        leaguesError = null
        vm.retryLeagues()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.leaguesStatus)
        assertEquals(listOf(LCK, LEC), vm.uiState.value.leagues.map { it.id })
    }

    @Test
    fun `the bell starts off when the user never chose`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.remindersEnabled)
        assertNull(vm.uiState.value.remindersNotice)
        assertEquals(0, reminderSync.requests)
    }

    @Test
    fun `the bell shows the stored choice`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
    }

    @Test
    fun `an unreadable bell counts as off`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true
        preferences.readFails = true

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.remindersEnabled)
    }

    @Test
    fun `turning the bell on remembers it and asks for a sync`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.enableReminders()
        assertTrue("flips at once", vm.uiState.value.remindersEnabled)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertEquals(listOf(true), preferences.savedMatchRemindersEnabled)
        assertEquals(1, reminderSync.requests)
    }

    @Test
    fun `a granted permission turns the bell on`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNotificationPermissionResult(granted = true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertEquals(listOf(true), preferences.savedMatchRemindersEnabled)
        assertEquals(1, reminderSync.requests)
    }

    @Test
    fun `a denied permission leaves the bell off and says why`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNotificationPermissionResult(granted = false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.remindersEnabled)
        assertEquals(
            "Reminders need permission to send notifications. Allow notifications for LolSito in the system settings.",
            vm.uiState.value.remindersNotice,
        )
        assertTrue(preferences.savedMatchRemindersEnabled.isEmpty())
        assertEquals(0, reminderSync.requests)
    }

    @Test
    fun `turning the bell on after a denial clears the notice`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNotificationPermissionResult(granted = false)

        vm.enableReminders()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertNull(vm.uiState.value.remindersNotice)
    }

    @Test
    fun `turning the bell off cancels every reminder at once and remembers it`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.disableReminders()
        assertFalse("flips at once", vm.uiState.value.remindersEnabled)
        assertEquals("cancelled without waiting for a sync", 1, reminderScheduler.cancelAllCalls)
        advanceUntilIdle()

        assertEquals(listOf(false), preferences.savedMatchRemindersEnabled)
        assertEquals(0, reminderSync.requests)
    }

    @Test
    fun `a failed save is ignored and the bell still flips`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        preferences.writeFails = true

        vm.enableReminders()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.remindersEnabled)

        vm.disableReminders()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.remindersEnabled)
        assertEquals(1, reminderScheduler.cancelAllCalls)
    }

    @Test
    fun `the bell does not touch the favorites or the teams`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(t1)
        val vm = viewModel()
        advanceUntilIdle()

        vm.enableReminders()
        vm.disableReminders()
        advanceUntilIdle()

        assertEquals(listOf(t1), vm.uiState.value.favorites)
        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
    }

    private fun team(name: String, code: String, id: String) = Team(name, code, null, null, null, id = id)

    private fun standingsOf(vararg teams: Team) = Standings(
        listOf(StandingsStage("Season", null, listOf(StandingsSection("Table", teams.mapIndexed { i, t -> Ranking(i + 1, t, 0, 0) }, emptyList())))),
    )

    private class FakeStandingsRepository : StandingsRepository {
        val tournaments = mutableMapOf<String, List<Tournament>>()
        val standings = mutableMapOf<String, Standings>()
        val standingsRequests = mutableListOf<String>()
        var error: Throwable? = null

        override suspend fun getTournaments(leagueId: String): List<Tournament> {
            error?.let { throw it }
            return tournaments[leagueId].orEmpty()
        }

        override suspend fun getStandings(tournamentId: String): Standings {
            error?.let { throw it }
            standingsRequests += tournamentId
            return standings.getValue(tournamentId)
        }
    }

    private companion object {
        const val LCK = "98767991310872058"
        const val LEC = "98767991302996019"
    }
}
