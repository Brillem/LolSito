package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.polling.MATCH_DETAIL_POLL_INTERVAL
import com.example.lolsito.feature.match.domain.FormEntry
import com.example.lolsito.feature.match.domain.FormOutcome
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.match.domain.MatchHistory
import com.example.lolsito.feature.match.domain.TeamForm
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.FakeTeamsRepository
import com.example.lolsito.testutil.FakeUserPreferences
import com.example.lolsito.testutil.series
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

/** Recent form and head to head of Match detail: how [MatchDetailViewModel] loads and exposes them. */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMatchDetailRepository()
    private val preferences = FakeUserPreferences()
    private val teams = FakeTeamsRepository().apply {
        profiles[ALPHA_ID] = profile(ALPHA_ID, "Alpha", homeLeagueName = "Home A")
        profiles[BETA_ID] = profile(BETA_ID, "Beta", homeLeagueName = "Home B")
    }
    private val schedule = FakeScheduleRepository()
    private var leagues: List<League> = listOf(league(HOME_A, "Home A"), league(HOME_B, "Home B"))
    private var leaguesError: Throwable? = null
    private var leagueRequests = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the history is asked once for both teams, with their home leagues and the international ones in one filter`() =
        runTest(dispatcher) {
            schedule.pages[null] = page(fullHistory(), older = "o1")

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(listOf(ALPHA_ID, BETA_ID), teams.requests)
            assertEquals(1, leagueRequests)
            assertEquals(listOf((InternationalLeagues.ids + listOf(HOME_A, HOME_B)).sorted() to null), schedule.requests)
            val history = (vm.historyState.value as MatchHistoryUiState.Content).history
            assertEquals(listOf("Alpha", "Beta"), history.form.map { it.team.name })
            assertEquals(listOf("a5", "a4", "a3", "a2", "a1"), history.form[0].entries.map { it.matchId })
            assertEquals(listOf("b5", "b4", "b3", "b2", "b1"), history.form[1].entries.map { it.matchId })
        }

    @Test
    fun `the current match counts neither in the form nor in the head to head`() = runTest(dispatcher) {
        schedule.pages[null] = page(
            listOf(
                series("previous", 1, "Alpha", "Beta", firstWon = true),
                series(MATCH_ID, 2, "Alpha", "Beta", firstWon = false),
            ),
        )

        val vm = viewModel()
        advanceUntilIdle()

        val history = (vm.historyState.value as MatchHistoryUiState.Content).history
        assertEquals(listOf(FormEntry("previous", FormOutcome.WIN, "BET")), history.form[0].entries)
        assertEquals(listOf(FormEntry("previous", FormOutcome.LOSS, "ALP")), history.form[1].entries)
        assertEquals(listOf("previous"), history.meetings.map { it.matchId })
    }

    @Test
    fun `a team without finished series has an empty form and no meetings`() = runTest(dispatcher) {
        schedule.pages[null] = page(listOf(series("upcoming", 9, "Alpha", "Beta", state = MatchState.UNSTARTED)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            MatchHistoryUiState.Content(
                MatchHistory(
                    form = listOf(TeamForm(ALPHA, emptyList()), TeamForm(BETA, emptyList())),
                    meetings = emptyList(),
                ),
            ),
            vm.historyState.value,
        )
    }

    @Test
    fun `no older page is requested when both teams have five finished series`() = runTest(dispatcher) {
        schedule.pages[null] = page(fullHistory(), older = "o1")

        viewModel()
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), schedule.requests.map { it.second })
    }

    @Test
    fun `older pages are requested with the same filter until both teams have five finished series`() =
        runTest(dispatcher) {
            schedule.pages[null] = page(fullHistory().filterNot { it.matchId == "b1" }, older = "o1")
            schedule.pages["o1"] = page(listOf(series("b0", 1, "Beta", "Gamma", firstWon = true)), older = "o2")

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(listOf(null, "o1"), schedule.requests.map { it.second })
            assertEquals(1, schedule.requests.map { it.first }.distinct().size)
            val history = (vm.historyState.value as MatchHistoryUiState.Content).history
            assertEquals(listOf("b5", "b4", "b3", "b2", "b0"), history.form[1].entries.map { it.matchId })
        }

    @Test
    fun `at most two older pages are requested however few series a team has`() = runTest(dispatcher) {
        schedule.pages[null] = page(emptyList(), older = "o1")
        schedule.pages["o1"] = page(emptyList(), older = "o2")
        schedule.pages["o2"] = page(listOf(series("a1", 1, "Alpha", "Gamma", firstWon = true)), older = "o3")
        schedule.pages["o3"] = page(emptyList(), older = "o4")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(null, "o1", "o2"), schedule.requests.map { it.second })
        val history = (vm.historyState.value as MatchHistoryUiState.Content).history
        assertEquals(listOf("a1"), history.form[0].entries.map { it.matchId })
    }

    @Test
    fun `paging stops when the schedule has nothing older`() = runTest(dispatcher) {
        schedule.pages[null] = page(emptyList(), older = null)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(1, schedule.requests.size)
        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
    }

    @Test
    fun `with results hidden the history state exposes no result, and revealing shows it`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        schedule.pages[null] = page(fullHistory())
        val vm = viewModel()
        val states = mutableListOf<MatchHistoryUiState>()
        backgroundScope.launch { vm.historyState.toList(states) }
        advanceUntilIdle()

        assertEquals(true, (vm.uiState.value as MatchDetailUiState.Content).resultsHidden)
        assertEquals(listOf<MatchHistoryUiState>(MatchHistoryUiState.None), states)

        vm.revealResult()
        runCurrent()

        val history = (vm.historyState.value as MatchHistoryUiState.Content).history
        assertEquals(5, history.form[0].entries.size)
    }

    @Test
    fun `revealing while the history is still loading shows it loading and then loaded`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        schedule.pages[null] = page(fullHistory())
        schedule.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MatchHistoryUiState.None, vm.historyState.value)

        vm.revealResult()
        assertEquals(MatchHistoryUiState.Loading, vm.historyState.value)

        schedule.gate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
    }

    @Test
    fun `a failed history load leaves the series content unchanged and retrying it recovers`() = runTest(dispatcher) {
        schedule.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MatchDetailUiState.Content(detail()), vm.uiState.value)
        assertEquals(MatchHistoryUiState.Error, vm.historyState.value)

        schedule.error = null
        schedule.pages[null] = page(fullHistory())
        vm.retryHistory()
        assertEquals(MatchHistoryUiState.Loading, vm.historyState.value)
        advanceUntilIdle()

        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
        assertEquals(MatchDetailUiState.Content(detail()), vm.uiState.value)
        assertEquals(listOf(MATCH_ID), repository.requests) // the series was not asked for again
    }

    @Test
    fun `a failure to find the teams or the leagues is a failed history load too`() = runTest(dispatcher) {
        schedule.pages[null] = page(fullHistory())
        teams.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MatchHistoryUiState.Error, vm.historyState.value)

        teams.error = null
        leaguesError = IOException("offline")
        vm.retryHistory()
        advanceUntilIdle()
        assertEquals(MatchHistoryUiState.Error, vm.historyState.value)
        assertEquals(MatchDetailUiState.Content(detail()), vm.uiState.value)
        assertTrue(schedule.requests.isEmpty())
    }

    @Test
    fun `the series is shown without waiting for the history`() = runTest(dispatcher) {
        schedule.pages[null] = page(fullHistory())
        schedule.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MatchDetailUiState.Content(detail()), vm.uiState.value)
        assertEquals(MatchHistoryUiState.Loading, vm.historyState.value)

        schedule.gate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
    }

    @Test
    fun `polling an in-progress series keeps refreshing it and never reloads the history`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.IN_PROGRESS, "Round 1")
        schedule.pages[null] = page(fullHistory())
        val vm = viewModel()
        advanceUntilIdle()
        val history = vm.historyState.value
        val polling = launch { vm.pollWhileVisible() }

        repository.detail = detail(MatchState.IN_PROGRESS, "Round 2")
        advanceTimeBy(MATCH_DETAIL_POLL_INTERVAL)
        runCurrent()
        repository.detail = detail(MatchState.IN_PROGRESS, "Round 3")
        advanceTimeBy(MATCH_DETAIL_POLL_INTERVAL)
        runCurrent()
        polling.cancel()

        assertEquals(MatchDetailUiState.Content(detail(MatchState.IN_PROGRESS, "Round 3")), vm.uiState.value)
        assertEquals(3, repository.requests.size)
        assertEquals(1, schedule.requests.size)
        assertEquals(listOf(ALPHA_ID, BETA_ID), teams.requests)
        assertTrue(history is MatchHistoryUiState.Content)
        assertEquals(history, vm.historyState.value)
    }

    @Test
    fun `retrying a failed series load starts the history once the series is known`() = runTest(dispatcher) {
        repository.error = IOException("boom")
        schedule.pages[null] = page(fullHistory())
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MatchDetailUiState.Error("boom"), vm.uiState.value)
        assertTrue(schedule.requests.isEmpty())

        repository.error = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(MatchDetailUiState.Content(detail()), vm.uiState.value)
        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
        assertEquals(1, schedule.requests.size)
    }

    @Test
    fun `teams without an id or a known home league are searched in the international leagues only`() =
        runTest(dispatcher) {
            repository.detail = detail(teams = listOf(ALPHA.copy(id = null), BETA))
            teams.profiles[BETA_ID] = profile(BETA_ID, "Beta", homeLeagueName = "Unlisted league")
            schedule.pages[null] = page(fullHistory())

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(listOf(BETA_ID), teams.requests)
            assertEquals(listOf(InternationalLeagues.ids.sorted() to null), schedule.requests)
            assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
        }

    @Test
    fun `a series without two teams has no history and asks for none`() = runTest(dispatcher) {
        repository.detail = detail(teams = listOf(ALPHA))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MatchHistoryUiState.None, vm.historyState.value)
        assertTrue(teams.requests.isEmpty())
        assertTrue(schedule.requests.isEmpty())
    }

    @Test
    fun `the start time is that of the open match among the history events`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.pages[null] = page(fullHistory() + series(MATCH_ID, 9, "Alpha", "Beta", state = MatchState.UNSTARTED))
        val vm = viewModel()
        assertNull(vm.startTime.value)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-01-09T12:00:00Z"), vm.startTime.value)
    }

    @Test
    fun `there is no start time when the open match is not among the history events`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.pages[null] = page(fullHistory())
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
        assertNull(vm.startTime.value)
    }

    @Test
    fun `there is no start time when the history cannot be loaded`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.startTime.value)
    }

    @Test
    fun `the start time is known while results are hidden, and so is the calendar event`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.pages[null] = page(fullHistory() + series(MATCH_ID, 9, "Alpha", "Beta", state = MatchState.UNSTARTED))
        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.uiState.value as MatchDetailUiState.Content
        assertTrue(content.resultsHidden)
        assertEquals(MatchHistoryUiState.None, vm.historyState.value)
        assertEquals(Instant.parse("2026-01-09T12:00:00Z"), vm.startTime.value)
        assertEquals(Instant.parse("2026-01-09T12:00:00Z"), content.calendarEvent(vm.startTime.value)?.start)
    }

    @Test
    fun `refresh reloads the history and the start time, keeping the history on screen meanwhile`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.pages[null] = page(fullHistory() + series(MATCH_ID, 9, "Alpha", "Beta", state = MatchState.UNSTARTED))
        val vm = viewModel()
        advanceUntilIdle()
        val states = mutableListOf<MatchHistoryUiState>()
        backgroundScope.launch { vm.historyState.toList(states) }
        runCurrent()
        val shown = vm.historyState.value

        schedule.pages[null] = page(
            fullHistory() + series("a6", 6, "Alpha", "Gamma", firstWon = true) +
                series(MATCH_ID, 10, "Alpha", "Beta", state = MatchState.UNSTARTED),
        )
        schedule.gate = CompletableDeferred()
        vm.refresh()
        runCurrent()
        assertTrue(vm.isRefreshing.value)
        assertEquals(shown, vm.historyState.value)

        schedule.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
        val history = (vm.historyState.value as MatchHistoryUiState.Content).history
        assertEquals("a6", history.form[0].entries.first().matchId)
        assertEquals(Instant.parse("2026-01-10T12:00:00Z"), vm.startTime.value)
        assertTrue("the history never goes back to loading", states.all { it is MatchHistoryUiState.Content })
        assertEquals(listOf(MATCH_ID, MATCH_ID), repository.requests)
    }

    @Test
    fun `a refresh whose history fails keeps the history and the start time`() = runTest(dispatcher) {
        repository.detail = detail(MatchState.UNSTARTED)
        schedule.pages[null] = page(fullHistory() + series(MATCH_ID, 9, "Alpha", "Beta", state = MatchState.UNSTARTED))
        val vm = viewModel()
        advanceUntilIdle()
        val shown = vm.historyState.value

        schedule.error = IOException("offline")
        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
        assertEquals(shown, vm.historyState.value)
        assertEquals(Instant.parse("2026-01-09T12:00:00Z"), vm.startTime.value)
        assertEquals(MatchDetailUiState.Content(detail(MatchState.UNSTARTED)), vm.uiState.value)
    }

    @Test
    fun `a refresh loads again a history that had failed`() = runTest(dispatcher) {
        schedule.error = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MatchHistoryUiState.Error, vm.historyState.value)

        schedule.error = null
        schedule.pages[null] = page(fullHistory())
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.historyState.value is MatchHistoryUiState.Content)
        assertFalse(vm.isRefreshing.value)
    }

    private fun viewModel() = MatchDetailViewModel(
        matchId = MATCH_ID,
        repository = repository,
        preferences = preferences,
        teamsRepository = teams,
        scheduleRepository = schedule,
        leaguesRepository = {
            leagueRequests++
            leaguesError?.let { throw it }
            leagues
        },
        zoneId = ZoneOffset.UTC,
    )

    /** Five finished series for each team against a third one, on January 1 to 5: "a1".."a5" and "b1".."b5". */
    private fun fullHistory(): List<ScheduleEvent> = (1..5).flatMap { day ->
        listOf(
            series("a$day", day, "Alpha", "Gamma", firstWon = true),
            series("b$day", day, "Gamma", "Beta", firstWon = true),
        )
    }

    private fun page(events: List<ScheduleEvent>, older: String? = null) =
        SchedulePage(events, olderToken = older, newerToken = null)

    private fun detail(
        state: MatchState = MatchState.COMPLETED,
        blockName: String? = null,
        teams: List<Team> = listOf(ALPHA, BETA),
    ) = MatchDetail(
        matchId = MATCH_ID,
        state = state,
        leagueName = "League",
        leagueSlug = "league",
        leagueImageUrl = null,
        blockName = blockName,
        teams = teams,
        format = SeriesFormat(SeriesType.BEST_OF, 5),
        games = emptyList(),
    )

    private fun profile(id: String, name: String, homeLeagueName: String?) = TeamProfile(
        id = id,
        slug = name.lowercase(),
        name = name,
        code = name.take(3).uppercase(),
        imageUrl = null,
        homeLeagueName = homeLeagueName,
        homeLeagueRegion = null,
        roster = emptyList(),
    )

    private fun league(id: String, name: String) = League(id, name.lowercase(), name, region = null, imageUrl = null)

    private inner class FakeMatchDetailRepository : MatchDetailRepository {
        var detail: MatchDetail? = null
        var error: Throwable? = null
        val requests = mutableListOf<String>()

        override suspend fun getMatchDetail(matchId: String): MatchDetail {
            requests += matchId
            error?.let { throw it }
            return detail ?: detail()
        }
    }

    private companion object {
        const val MATCH_ID = "current"
        const val ALPHA_ID = "team-a"
        const val BETA_ID = "team-b"
        const val HOME_A = "500"
        const val HOME_B = "400"
        val ALPHA = Team("Alpha", "ALP", null, null, null, id = ALPHA_ID)
        val BETA = Team("Beta", "BET", null, null, null, id = BETA_ID)
    }
}
