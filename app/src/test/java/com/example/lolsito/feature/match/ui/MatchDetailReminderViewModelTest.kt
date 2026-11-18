package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.teams.ui.TeamsViewModel
import com.example.lolsito.testutil.FakeReminderSyncTrigger
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.FakeTeamsRepository
import com.example.lolsito.testutil.FakeUserPreferences
import com.example.lolsito.testutil.series
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
import java.time.Instant
import java.time.ZoneOffset

/** The "Remind me" button of Match detail: when [MatchDetailViewModel] offers it and what tapping it does. */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailReminderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var detail = detail(MatchState.UNSTARTED)
    private val repository = object : MatchDetailRepository {
        override suspend fun getMatchDetail(matchId: String): MatchDetail = detail
    }
    private val preferences = FakeUserPreferences()
    private val schedule = FakeScheduleRepository().apply {
        pages[null] = SchedulePage(listOf(series(MATCH_ID, 9, "Alpha", "Beta", state = MatchState.UNSTARTED)), null, null)
    }
    private var leagues = listOf(League(LEAGUE_ID, "league", "League", region = null, imageUrl = null))
    private val sync = FakeReminderSyncTrigger()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MatchDetailViewModel(
        matchId = MATCH_ID,
        repository = repository,
        preferences = preferences,
        teamsRepository = FakeTeamsRepository(),
        scheduleRepository = schedule,
        leaguesRepository = { leagues },
        zoneId = ZoneOffset.UTC,
        reminderSync = sync,
    )

    @Test
    fun `an unstarted match with a known start time offers "Remind me"`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MatchReminderButton(isSet = false), vm.reminderButton.value)
    }

    @Test
    fun `a match in progress or completed offers no reminder`() = runTest(dispatcher) {
        for (state in listOf(MatchState.IN_PROGRESS, MatchState.COMPLETED)) {
            detail = detail(state)
            val vm = viewModel()
            advanceUntilIdle()

            assertNull("for $state", vm.reminderButton.value)
        }
    }

    @Test
    fun `an unstarted match without a known start time offers no reminder`() = runTest(dispatcher) {
        schedule.pages[null] = SchedulePage(emptyList(), null, null)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.reminderButton.value)
    }

    @Test
    fun `setting the reminder stores the match with its league and asks for a sync`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setReminder()
        advanceUntilIdle()

        assertEquals(MatchReminderButton(isSet = true), vm.reminderButton.value)
        assertEquals(
            listOf(MarkedMatch(MATCH_ID, LEAGUE_ID, START, "ALP vs BET", "League · Bo5")),
            preferences.markedMatches,
        )
        assertEquals(1, sync.requests)
    }

    @Test
    fun `a match already marked reads as set, and removing it unmarks it and asks for a sync`() = runTest(dispatcher) {
        preferences.markedMatches = listOf(MarkedMatch(MATCH_ID, LEAGUE_ID, START, "ALP vs BET", "League · Bo5"))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MatchReminderButton(isSet = true), vm.reminderButton.value)

        vm.removeReminder()
        advanceUntilIdle()

        assertEquals(MatchReminderButton(isSet = false), vm.reminderButton.value)
        assertEquals(emptyList<MarkedMatch>(), preferences.markedMatches)
        assertEquals(1, sync.requests)
    }

    @Test
    fun `a granted permission sets the reminder, a denied one says why and sets nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNotificationPermissionResult(granted = false)
        advanceUntilIdle()
        assertEquals(MatchReminderButton(isSet = false, notice = TeamsViewModel.PERMISSION_DENIED_NOTICE), vm.reminderButton.value)
        assertTrue(preferences.savedMarkedMatches.isEmpty())

        vm.onNotificationPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(MatchReminderButton(isSet = true), vm.reminderButton.value)
        assertEquals(1, preferences.savedMarkedMatches.size)
    }

    @Test
    fun `a reminder that cannot be stored stays unset and says so`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        leagues = emptyList() // The league of the match cannot be found.
        vm.setReminder()
        advanceUntilIdle()
        assertEquals(MatchReminderButton(isSet = false, notice = MatchDetailViewModel.REMINDER_FAILED_NOTICE), vm.reminderButton.value)

        leagues = listOf(League(LEAGUE_ID, "league", "League", region = null, imageUrl = null))
        preferences.writeFails = true
        vm.setReminder()
        advanceUntilIdle()
        assertEquals(MatchReminderButton(isSet = false, notice = MatchDetailViewModel.REMINDER_FAILED_NOTICE), vm.reminderButton.value)
        assertEquals(0, sync.requests)
    }

    @Test
    fun `an unreadable store reads the match as not marked`() = runTest(dispatcher) {
        preferences.readFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MatchReminderButton(isSet = false), vm.reminderButton.value)
    }

    private fun detail(state: MatchState) = MatchDetail(
        matchId = MATCH_ID,
        state = state,
        leagueName = "League",
        leagueSlug = "league",
        leagueImageUrl = null,
        blockName = null,
        teams = listOf(Team("Alpha", "ALP", null, null, null), Team("Beta", "BET", null, null, null)),
        format = SeriesFormat(SeriesType.BEST_OF, 5),
        games = emptyList(),
    )

    private companion object {
        const val MATCH_ID = "current"
        const val LEAGUE_ID = "lid"
        val START: Instant = Instant.parse("2026-01-09T12:00:00Z")
    }
}
