package com.example.lolsito.feature.teams.ui

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.ui.eventRowDisplay
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TeamProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val teams = FakeTeamsRepository()
    private val schedule = FakeScheduleRepository()
    private val preferences = FakeUserPreferences()
    private var leaguesError: Throwable? = null
    private val leaguesRepository = LeaguesRepository {
        leaguesError?.let { throw it }
        listOf(League(LCK, "lck", "LCK", "KOREA", null), League(InternationalLeagues.WORLDS, "worlds", "Worlds", null, null))
    }

    private val lckFilter = (InternationalLeagues.ids + LCK).sorted()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        teams.profiles[T1_ID] = TeamProfile(T1_ID, "t1", "T1", "T1", null, "LCK", "KOREA", emptyList())
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(
                event("p1", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G"),
                event("other", "2026-10-02T10:00:00Z", MatchState.COMPLETED, "Gen.G", "KT Rolster"),
                event("u1", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1"),
            ),
            olderToken = "older-1",
            newerToken = null,
        )
        schedule.pages[lckFilter to "older-1"] = SchedulePage(
            events = listOf(
                event("p0", "2026-08-01T10:00:00Z", MatchState.COMPLETED, "T1", "DRX"),
                event("other-old", "2026-08-02T10:00:00Z", MatchState.COMPLETED, "Gen.G", "DRX"),
                // The same match again (pages overlap): it must show once.
                event("p1", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G"),
            ),
            olderToken = null,
            newerToken = "newer-1",
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(originLeagueId: String? = null) = TeamProfileViewModel(
        teamId = T1_ID,
        originLeagueId = originLeagueId,
        teamsRepository = teams,
        scheduleRepository = schedule,
        leaguesRepository = leaguesRepository,
        preferences = preferences,
        zoneId = ZoneOffset.UTC,
    )

    private val TeamProfileUiState.upcomingIds get() = upcomingDays.flatMap { day -> day.events.map { it.matchId } }
    private val TeamProfileUiState.previousIds get() = previousDays.flatMap { day -> day.events.map { it.matchId } }

    @Test
    fun `loads the profile, then the team's matches of its home league and the international ones`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(LoadStatus.Loading, vm.uiState.value.status)
        assertEquals(LoadStatus.Loading, vm.uiState.value.matchesStatus)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadStatus.Content, state.status)
        assertEquals("T1", state.profile?.name)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertEquals(listOf("u1"), state.upcomingIds)
        assertEquals(listOf("p1"), state.previousIds)
        assertTrue(state.canLoadOlder)
        assertFalse(state.isLoadingOlder)
        assertEquals(listOf(lckFilter to null), schedule.requests)
        assertEquals(listOf(T1_ID), teams.requests)
    }

    @Test
    fun `an unknown home league falls back to the league the team was opened from`() = runTest(dispatcher) {
        teams.profiles[T1_ID] = teams.profiles.getValue(T1_ID).copy(homeLeagueName = "Somewhere")

        viewModel(originLeagueId = LCK)
        advanceUntilIdle()

        assertEquals(listOf(lckFilter to null), schedule.requests)
    }

    @Test
    fun `failed leagues fall back to the league the team was opened from`() = runTest(dispatcher) {
        leaguesError = IOException("no leagues")

        val vm = viewModel(originLeagueId = LCK)
        advanceUntilIdle()

        assertEquals(listOf(lckFilter to null), schedule.requests)
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    @Test
    fun `without a home league or an origin only the international leagues are asked`() = runTest(dispatcher) {
        teams.profiles[T1_ID] = teams.profiles.getValue(T1_ID).copy(homeLeagueName = null)
        schedule.pages[InternationalLeagues.ids.sorted() to null] = SchedulePage(emptyList(), olderToken = null, newerToken = null)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(InternationalLeagues.ids.sorted() to null), schedule.requests)
        assertEquals(LoadStatus.Empty, vm.uiState.value.matchesStatus)
    }

    @Test
    fun `load older pages with the same filter, merges and stops when there is no older page`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        assertTrue(vm.uiState.value.isLoadingOlder)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(lckFilter to null, lckFilter to "older-1"), schedule.requests)
        assertEquals(listOf("u1"), state.upcomingIds)
        assertEquals(listOf("p1", "p0"), state.previousIds)
        assertFalse(state.canLoadOlder)
        assertFalse(state.isLoadingOlder)

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(2, schedule.requests.size)
    }

    @Test
    fun `a second load older while one is in flight is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        vm.loadOlder()
        advanceUntilIdle()

        assertEquals(listOf(lckFilter to null, lckFilter to "older-1"), schedule.requests)
    }

    @Test
    fun `a failed older page keeps the matches, shows the error and can be retried`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        schedule.error = IOException("timeout")

        vm.loadOlder()
        advanceUntilIdle()

        var state = vm.uiState.value
        assertEquals("timeout", state.olderError)
        assertFalse(state.isLoadingOlder)
        assertTrue(state.canLoadOlder)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertEquals(listOf("p1"), state.previousIds)

        schedule.error = null
        vm.loadOlder()
        advanceUntilIdle()

        state = vm.uiState.value
        assertNull(state.olderError)
        assertEquals(listOf("p1", "p0"), state.previousIds)
    }

    @Test
    fun `a failed profile is an error, asks for no matches, and retry loads everything`() = runTest(dispatcher) {
        teams.error = IOException("offline")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Error("offline"), vm.uiState.value.status)
        assertNull(vm.uiState.value.profile)
        assertEquals(emptyList<Any>(), schedule.requests)

        teams.error = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
        assertEquals(listOf("p1"), vm.uiState.value.previousIds)
    }

    @Test
    fun `an unknown team is empty and asks for no matches`() = runTest(dispatcher) {
        teams.profiles.clear()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Empty, vm.uiState.value.status)
        assertEquals(emptyList<Any>(), schedule.requests)
    }

    @Test
    fun `failed matches keep the profile and retry loads them again`() = runTest(dispatcher) {
        schedule.error = IOException("no schedule")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.status)
        assertEquals(LoadStatus.Error("no schedule"), vm.uiState.value.matchesStatus)
        assertFalse(vm.uiState.value.canLoadOlder)

        schedule.error = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
        assertEquals(listOf("u1"), vm.uiState.value.upcomingIds)
    }

    @Test
    fun `no team match yet but an older page stays content so it can be loaded`() = runTest(dispatcher) {
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(event("other", "2026-10-02T10:00:00Z", MatchState.COMPLETED, "Gen.G", "KT Rolster")),
            olderToken = "older-1",
            newerToken = null,
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
        assertEquals(emptyList<Any>(), vm.uiState.value.upcomingDays)
        assertEquals(emptyList<Any>(), vm.uiState.value.previousDays)
        assertTrue(vm.uiState.value.canLoadOlder)
    }

    @Test
    fun `no spoilers is off by default and previous matches show their score`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.noSpoilers)
        val previous = state.previousDays.single().events.single()
        assertEquals("2 - 0", eventRowDisplay(previous, isLive = false, noSpoilers = state.noSpoilers).score)
    }

    @Test
    fun `a stored no spoilers mode is read before any match shows and hides previous results`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        val seen = mutableListOf<TeamProfileUiState>()

        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { seen += it } }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.noSpoilers)
        assertTrue(seen.filter { it.previousDays.isNotEmpty() }.all { it.noSpoilers })
        val display = eventRowDisplay(state.previousDays.single().events.single(), isLive = false, noSpoilers = state.noSpoilers)
        assertEquals("vs", display.score)
        assertNull(display.result.winnerIndex)
    }

    @Test
    fun `an unreadable no spoilers mode counts as off`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        preferences.readFails = true

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.noSpoilers)
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    private fun event(id: String, start: String, state: MatchState, first: String, second: String): ScheduleEvent {
        val done = state == MatchState.COMPLETED
        return ScheduleEvent(
            startTime = Instant.parse(start),
            state = state,
            blockName = null,
            leagueName = "LCK",
            leagueSlug = "lck",
            match = Match(
                id,
                listOf(
                    Team(first, first.take(3), null, null, if (done) 2 else null),
                    Team(second, second.take(3), null, null, if (done) 0 else null),
                ),
                null,
            ),
        )
    }

    private class FakeTeamsRepository : TeamsRepository {
        val profiles = mutableMapOf<String, TeamProfile>()
        val requests = mutableListOf<String>()
        var error: Throwable? = null

        override suspend fun getTeam(id: String): TeamProfile? {
            requests += id
            error?.let { throw it }
            return profiles[id]
        }
    }

    private class FakeScheduleRepository : ScheduleRepository {
        val pages = mutableMapOf<Pair<List<String>, String?>, SchedulePage>()
        val requests = mutableListOf<Pair<List<String>, String?>>()
        var error: Throwable? = null

        override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
            requests += leagueIds to pageToken
            error?.let { throw it }
            return pages.getValue(leagueIds to pageToken)
        }
    }

    private companion object {
        const val LCK = "98767991310872058"
        const val T1_ID = "98767991853197861"
    }
}
