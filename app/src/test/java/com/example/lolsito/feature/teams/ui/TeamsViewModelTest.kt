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
import com.example.lolsito.feature.teams.domain.DirectoryTeam
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.feature.teams.domain.TeamDirectoryRepository
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.testutil.FakeNotificationAvailability
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
    private val notifications = FakeNotificationAvailability()
    private val directory = FakeDirectory()

    private fun viewModel() =
        TeamsViewModel(repository, leaguesRepository, preferences, clock, reminderScheduler, reminderSync, notifications, directory, defaultLeagueId = LCK)

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
    fun `the bell on with system notifications off says so and stays on`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true
        notifications.allowed = false

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertEquals(TeamsViewModel.NOTIFICATIONS_OFF_NOTICE, vm.uiState.value.remindersNotice)
        assertTrue("the bell is not turned off for the user", preferences.savedMatchRemindersEnabled.isEmpty())
        assertEquals(0, reminderScheduler.cancelAllCalls)
    }

    @Test
    fun `the bell on with system notifications on shows no notice`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true

        val vm = viewModel()
        advanceUntilIdle()
        vm.refreshNotificationsNotice()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertNull(vm.uiState.value.remindersNotice)
    }

    @Test
    fun `the bell off shows no notice with system notifications off`() = runTest(dispatcher) {
        notifications.allowed = false

        val vm = viewModel()
        advanceUntilIdle()
        vm.refreshNotificationsNotice()

        assertFalse(vm.uiState.value.remindersEnabled)
        assertNull(vm.uiState.value.remindersNotice)
    }

    @Test
    fun `the notice follows the system setting each time the screen is shown again`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = true
        val vm = viewModel()
        advanceUntilIdle()

        notifications.allowed = false
        vm.refreshNotificationsNotice()
        assertEquals(TeamsViewModel.NOTIFICATIONS_OFF_NOTICE, vm.uiState.value.remindersNotice)

        notifications.allowed = true
        vm.refreshNotificationsNotice()
        assertNull(vm.uiState.value.remindersNotice)
        assertTrue(vm.uiState.value.remindersEnabled)
    }

    @Test
    fun `turning the bell on with system notifications off says so`() = runTest(dispatcher) {
        notifications.allowed = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.enableReminders()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertEquals(TeamsViewModel.NOTIFICATIONS_OFF_NOTICE, vm.uiState.value.remindersNotice)
        assertEquals(1, reminderSync.requests)
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

    @Test
    fun `the team list is not requested until the user types`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(0, directory.requests)
    }

    @Test
    fun `typing requests the team list once and searches it, also for later queries`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("fa")
        assertEquals(LoadStatus.Loading, vm.uiState.value.searchStatus)
        advanceUntilIdle()
        vm.onQueryChange("faker")
        advanceUntilIdle()

        assertEquals(1, directory.requests)
        assertEquals(LoadStatus.Content, vm.uiState.value.searchStatus)
        assertEquals(listOf("Faker"), vm.uiState.value.searchResults.players.map { it.player.summonerName })
        assertEquals(listOf("T1"), vm.uiState.value.searchResults.players.map { it.team.code })
    }

    @Test
    fun `typing twice before the list arrives still requests it once`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("t")
        vm.onQueryChange("t1")
        advanceUntilIdle()

        assertEquals(1, directory.requests)
        assertEquals(listOf("T1"), vm.uiState.value.searchResults.teams.map { it.code })
    }

    @Test
    fun `a blank query goes back to the usual tab and keeps the list for the next search`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("t1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSearching)

        vm.clearQuery()

        val state = vm.uiState.value
        assertFalse(state.isSearching)
        assertEquals("", state.query)
        assertTrue(state.searchResults.isEmpty)
        assertEquals(listOf("2", "1"), state.teams.map { it.id })
        vm.onQueryChange("t1")
        advanceUntilIdle()
        assertEquals(1, directory.requests)
        assertEquals(1, vm.uiState.value.searchResults.teams.size)
    }

    @Test
    fun `a query shorter than two characters finds nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange(" t ")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.queryTooShort)
        assertTrue(vm.uiState.value.searchResults.isEmpty)
    }

    @Test
    fun `a failed list request shows an error and retrying requests it again`() = runTest(dispatcher) {
        directory.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("t1")
        advanceUntilIdle()
        assertEquals(LoadStatus.Error("offline"), vm.uiState.value.searchStatus)

        directory.error = null
        vm.retrySearch()
        assertEquals(LoadStatus.Loading, vm.uiState.value.searchStatus)
        advanceUntilIdle()

        assertEquals(2, directory.requests)
        assertEquals(LoadStatus.Content, vm.uiState.value.searchStatus)
        assertEquals(listOf("T1"), vm.uiState.value.searchResults.teams.map { it.code })
    }

    @Test
    fun `typing more after a failure does not request again until retry`() = runTest(dispatcher) {
        directory.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("t1")
        advanceUntilIdle()

        vm.onQueryChange("t1 ")
        advanceUntilIdle()

        assertEquals(1, directory.requests)
        assertTrue(vm.uiState.value.searchStatus is LoadStatus.Error)
    }

    private class FakeDirectory : TeamDirectoryRepository {
        var requests = 0
        var error: Throwable? = null
        val teams = listOf(
            DirectoryTeam(
                "1", "T1", "T1", null, "LCK", true,
                listOf(Player("p1", "Faker", "Sanghyeok", "Lee", null, PlayerRole.MID)),
            ),
            DirectoryTeam("2", "Gen.G", "GEN", null, "LCK", true, emptyList()),
        )

        override suspend fun getAllTeams(): List<DirectoryTeam> {
            requests++
            error?.let { throw it }
            return teams
        }
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
