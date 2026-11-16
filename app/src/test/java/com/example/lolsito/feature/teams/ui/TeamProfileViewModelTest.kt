package com.example.lolsito.feature.teams.ui

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.match.domain.FormEntry
import com.example.lolsito.feature.match.domain.FormOutcome
import com.example.lolsito.feature.schedule.ui.eventRowDisplay
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.feature.teams.domain.RoleGroup
import com.example.lolsito.feature.teams.domain.TeamLineupRepository
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.testutil.FakeReminderSyncTrigger
import com.example.lolsito.testutil.FakeUserPreferences
import com.example.lolsito.testutil.series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TeamProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val teams = FakeTeamsRepository()
    private val schedule = FakeScheduleRepository()
    private val preferences = FakeUserPreferences()
    private val lineups = FakeTeamLineupRepository()
    private val reminderSync = FakeReminderSyncTrigger()
    private val standings = FakeStandingsRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
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
        lineupRepository = lineups,
        standingsRepository = standings,
        clock = clock,
        zoneId = ZoneOffset.UTC,
        reminderSync = reminderSync,
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

    private val t1Favorite = FavoriteTeam(T1_ID, "t1", "T1", "T1", null)
    private val other = FavoriteTeam("777", "gen-g", "Gen.G", "GEN", "https://img/geng.png")

    @Test
    fun `a team that is not a favorite starts unmarked and nothing is saved`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFavorite)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
    }

    @Test
    fun `a stored favorite shows as marked`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(other, t1Favorite)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFavorite)
    }

    @Test
    fun `marking saves the team with id, slug, name, code and logo and keeps the other favorites`() = runTest(dispatcher) {
        teams.profiles[T1_ID] = teams.profiles.getValue(T1_ID).copy(imageUrl = "https://img/t1.png")
        preferences.favoriteTeams = listOf(other)
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavorite()
        assertTrue("flips at once", vm.uiState.value.isFavorite)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFavorite)
        assertEquals(
            listOf(listOf(other, FavoriteTeam(T1_ID, "t1", "T1", "T1", "https://img/t1.png"))),
            preferences.savedFavoriteTeams,
        )
    }

    @Test
    fun `unmarking removes only this team`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(t1Favorite, other)
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFavorite)
        assertEquals(listOf(listOf(other)), preferences.savedFavoriteTeams)
    }

    @Test
    fun `the mark is remembered by the next profile of the team`() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.toggleFavorite()
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()
        assertTrue(second.uiState.value.isFavorite)

        second.toggleFavorite()
        advanceUntilIdle()
        val third = viewModel()
        advanceUntilIdle()
        assertFalse(third.uiState.value.isFavorite)
        assertEquals(emptyList<FavoriteTeam>(), preferences.favoriteTeams)
    }

    @Test
    fun `marking and unmarking a favorite each ask for a reminder sync once saved`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("opening the profile changes no favorite", 0, reminderSync.requests)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertEquals(1, reminderSync.requests)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertEquals(2, reminderSync.requests)
    }

    @Test
    fun `a failed save changes no favorite and asks for no reminder sync`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        preferences.writeFails = true

        vm.toggleFavorite()
        advanceUntilIdle()

        assertEquals(0, reminderSync.requests)
    }

    @Test
    fun `a failed save is ignored and the star still flips`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        preferences.writeFails = true

        vm.toggleFavorite()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFavorite)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
    }

    @Test
    fun `unreadable favorites count as none, and a toggle then saves nothing so the stored ones are not lost`() = runTest(dispatcher) {
        preferences.favoriteTeams = listOf(other)
        preferences.readFails = true
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFavorite)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
        assertEquals(listOf(other), preferences.favoriteTeams)
    }

    @Test
    fun `toggling before the profile is known does nothing`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFavorite)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
    }

    @Test
    fun `toggling for an unknown team does nothing`() = runTest(dispatcher) {
        teams.profiles.clear()
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFavorite)
        assertTrue(preferences.savedFavoriteTeams.isEmpty())
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

    private fun player(id: String, name: String, role: PlayerRole) = Player(id, name, null, null, null, role)

    private val faker = player("f", "Faker", PlayerRole.MID)
    private val subMid = player("s", "Sub", PlayerRole.MID)
    private val zeus = player("z", "Zeus", PlayerRole.TOP)

    private fun withRoster() {
        teams.profiles[T1_ID] = teams.profiles.getValue(T1_ID).copy(
            roster = listOf(RoleGroup(PlayerRole.TOP, listOf(zeus)), RoleGroup(PlayerRole.MID, listOf(subMid, faker))),
        )
    }

    @Test
    fun `the roster is shown before the lineup arrives, then the starters are marked`() = runTest(dispatcher) {
        withRoster()
        val answer = CompletableDeferred<Set<String>?>()
        lineups.answers["p1"] = { answer.await() }

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
        assertEquals(listOf("p1"), vm.uiState.value.previousIds)
        assertEquals(2, vm.uiState.value.profile?.roster?.size)
        assertNull(vm.uiState.value.lineup)

        answer.complete(setOf("f", "z"))
        advanceUntilIdle()

        val lineup = vm.uiState.value.lineup!!
        assertEquals(listOf(zeus, faker), lineup.starters)
        assertEquals(listOf(RoleGroup(PlayerRole.MID, listOf(subMid))), lineup.rest)
        assertEquals(listOf("p1" to T1_ID), lineups.requests)
        assertEquals(2, vm.uiState.value.profile?.roster?.size)
    }

    @Test
    fun `the lineup is read from the newest finished matches first and falls back to the one before`() = runTest(dispatcher) {
        withRoster()
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(
                event("old", "2026-09-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G"),
                event("new", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "DRX"),
                event("next", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1"),
            ),
            olderToken = null,
            newerToken = null,
        )
        lineups.answers["old"] = { setOf("f") }

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("new" to T1_ID, "old" to T1_ID), lineups.requests)
        assertEquals(listOf(faker), vm.uiState.value.lineup?.starters)
    }

    @Test
    fun `a lineup that fails leaves the profile as it is, without an error`() = runTest(dispatcher) {
        withRoster()
        lineups.answers["p1"] = { throw IOException("feed down") }

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.lineup)
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertNull(state.olderError)
        assertEquals(listOf("p1"), state.previousIds)
        assertEquals(listOf(zeus), state.profile?.roster?.first()?.players)
    }

    @Test
    fun `a lineup with nobody from the roster leaves the roster as it is`() = runTest(dispatcher) {
        withRoster()
        lineups.answers["p1"] = { setOf("ghost") }

        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.lineup)
    }

    @Test
    fun `a reload does not keep a stale lineup`() = runTest(dispatcher) {
        withRoster()
        lineups.answers["p1"] = { setOf("f") }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(faker), vm.uiState.value.lineup?.starters)

        val pending = CompletableDeferred<Set<String>?>()
        lineups.answers["p1"] = { pending.await() }
        vm.retry()
        advanceUntilIdle()

        assertNull(vm.uiState.value.lineup)
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    @Test
    fun `a lineup still on its way when the profile reloads is dropped`() = runTest(dispatcher) {
        withRoster()
        val first = CompletableDeferred<Set<String>?>()
        lineups.answers["p1"] = { first.await() }
        val vm = viewModel()
        advanceUntilIdle()

        lineups.answers["p1"] = { null }
        vm.retry()
        advanceUntilIdle()
        first.complete(setOf("f"))
        advanceUntilIdle()

        assertNull(vm.uiState.value.lineup)
    }

    @Test
    fun `no finished match means no lineup request`() = runTest(dispatcher) {
        withRoster()
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(event("u1", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1")),
            olderToken = null,
            newerToken = null,
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(lineups.requests.isEmpty())
        assertNull(vm.uiState.value.lineup)
    }

    @Test
    fun `loading older matches does not ask for another lineup`() = runTest(dispatcher) {
        withRoster()
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadOlder()
        advanceUntilIdle()

        assertEquals(listOf("p1" to T1_ID), lineups.requests)
    }

    private class FakeTeamLineupRepository : TeamLineupRepository {
        val answers = mutableMapOf<String, suspend () -> Set<String>?>()
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun getLineup(matchId: String, teamId: String): Set<String>? {
            requests += matchId to teamId
            return answers[matchId]?.invoke()
        }
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

    private fun withStandings(ordinal: Int? = 3, wins: Int? = 12, losses: Int? = 4) {
        standings.tournaments[LCK] = listOf(Tournament("t-now", "now", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 1)))
        val ranking = Ranking(ordinal, Team("T1", "T1", null, null, null, id = T1_ID), wins, losses)
        standings.standings["t-now"] = Standings(listOf(StandingsStage("Season", null, listOf(StandingsSection("Table", listOf(ranking), emptyList())))))
    }

    @Test
    fun `the standing line of the home league arrives next to the matches`() = runTest(dispatcher) {
        withStandings()

        val vm = viewModel()
        assertNull(vm.uiState.value.standing)
        advanceUntilIdle()

        assertEquals("3rd in LCK · 12-4", vm.uiState.value.standing)
        // The recent tournaments ask for the same standings again (the repository caches them).
        assertEquals(listOf("t-now"), standings.standingsRequests.distinct())
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    @Test
    fun `a failed standings request leaves no line and the rest of the profile untouched`() = runTest(dispatcher) {
        withStandings()
        standings.error = IOException("standings down")

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.standing)
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertEquals(listOf("u1"), state.upcomingIds)
    }

    @Test
    fun `a team that is not ranked, or without a home league, has no line`() = runTest(dispatcher) {
        standings.tournaments[LCK] = listOf(Tournament("t-now", "now", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 1)))
        standings.standings["t-now"] = Standings(emptyList())
        assertNull(viewModel().also { advanceUntilIdle() }.uiState.value.standing)

        withStandings()
        teams.profiles[T1_ID] = teams.profiles.getValue(T1_ID).copy(homeLeagueName = null)
        schedule.pages[InternationalLeagues.ids.sorted() to null] = SchedulePage(emptyList(), olderToken = null, newerToken = null)
        assertNull(viewModel(originLeagueId = LCK).also { advanceUntilIdle() }.uiState.value.standing)
    }

    @Test
    fun `failed leagues leave no standing line`() = runTest(dispatcher) {
        withStandings()
        leaguesError = IOException("no leagues")

        val vm = viewModel(originLeagueId = LCK)
        advanceUntilIdle()

        assertNull(vm.uiState.value.standing)
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    private fun withRecentTournaments() {
        withStandings()
        standings.tournaments[LCK] = standings.tournaments.getValue(LCK) +
            Tournament("t-old", "old", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 1))
        standings.standings["t-old"] = Standings(
            listOf(StandingsStage("Season", null, listOf(StandingsSection("Table", listOf(Ranking(1, Team("T1", "T1", null, null, null, id = T1_ID), 9, 1)), emptyList())))),
        )
        // The team is not in the Worlds of the year, so it is not a recent tournament.
        standings.tournaments[InternationalLeagues.WORLDS] = listOf(Tournament("w", "worlds_2026", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15)))
        standings.standings["w"] = Standings(
            listOf(StandingsStage("Swiss", null, listOf(StandingsSection("Table", listOf(Ranking(1, Team("Gen.G", "GEN", null, null, null, id = "gen"), 3, 0)), emptyList())))),
        )
    }

    @Test
    fun `the recent tournaments the team played arrive with how far it got, most recent first`() = runTest(dispatcher) {
        withRecentTournaments()

        val vm = viewModel()
        assertTrue(vm.uiState.value.recentTournaments.isEmpty())
        advanceUntilIdle()

        assertEquals(
            listOf(
                RecentTournament(LCK, "t-now", "Now", "In progress", null),
                RecentTournament(LCK, "t-old", "Old", "1st", null),
            ),
            vm.uiState.value.recentTournaments,
        )
        assertEquals(LoadStatus.Content, vm.uiState.value.matchesStatus)
    }

    @Test
    fun `no spoilers hides the result of a finished tournament and keeps the one in progress`() = runTest(dispatcher) {
        withRecentTournaments()
        preferences.noSpoilers = true

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("In progress", null), vm.uiState.value.recentTournaments.map { it.result })
        assertEquals(listOf("Now", "Old"), vm.uiState.value.recentTournaments.map { it.name })
    }

    @Test
    fun `a tournament whose standings fail is left out and the others stay`() = runTest(dispatcher) {
        withRecentTournaments()
        standings.standings.remove("t-old")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("t-now"), vm.uiState.value.recentTournaments.map { it.tournamentId })
        assertEquals("3rd in LCK · 12-4", vm.uiState.value.standing)
    }

    @Test
    fun `when every request fails there are no recent tournaments and the rest is untouched`() = runTest(dispatcher) {
        withRecentTournaments()
        standings.error = IOException("standings down")

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.recentTournaments.isEmpty())
        assertEquals(LoadStatus.Content, state.status)
        assertEquals(LoadStatus.Content, state.matchesStatus)
        assertEquals(listOf("u1"), state.upcomingIds)
    }

    @Test
    fun `retrying loads the recent tournaments again without repeating them`() = runTest(dispatcher) {
        withRecentTournaments()
        val vm = viewModel()
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf("t-now", "t-old"), vm.uiState.value.recentTournaments.map { it.tournamentId })
    }

    private fun withFormEvents() {
        schedule.pages[lckFilter to null] = SchedulePage(
            events = (1..6).map { day -> series("m$day", day, "T1", "Gen.G", firstWon = day % 2 == 0) } +
                series("lost-to", 7, "DRX", "T1", firstWon = true) +
                series("unknown", 8, "T1", "KT Rolster", firstWon = null) +
                series("other", 9, "Gen.G", "DRX", firstWon = true) +
                series("next", 20, "T1", "Dplus KIA", state = MatchState.UNSTARTED),
            olderToken = null,
            newerToken = null,
        )
    }

    @Test
    fun `the recent form is the last five finished series, newest first, from the loaded events`() = runTest(dispatcher) {
        withFormEvents()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(
                FormEntry("lost-to", FormOutcome.LOSS, "DRX"),
                FormEntry("m6", FormOutcome.WIN, "GEN"),
                FormEntry("m5", FormOutcome.LOSS, "GEN"),
                FormEntry("m4", FormOutcome.WIN, "GEN"),
                FormEntry("m3", FormOutcome.LOSS, "GEN"),
            ),
            vm.uiState.value.form,
        )
        assertEquals(listOf(lckFilter to null), schedule.requests)
    }

    @Test
    fun `no finished matches means no form, and no spoilers hides it`() = runTest(dispatcher) {
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(event("u1", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1")),
            olderToken = null,
            newerToken = null,
        )
        assertEquals(emptyList<FormEntry>(), viewModel().also { advanceUntilIdle() }.uiState.value.form)

        withFormEvents()
        preferences.noSpoilers = true
        assertEquals(emptyList<FormEntry>(), viewModel().also { advanceUntilIdle() }.uiState.value.form)
    }

    @Test
    fun `the next match is the soonest upcoming one and stays in the upcoming list`() = runTest(dispatcher) {
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(
                event("later", "2026-11-25T10:00:00Z", MatchState.UNSTARTED, "T1", "DRX"),
                event("soon", "2026-11-20T10:00:00Z", MatchState.UNSTARTED, "Dplus KIA", "T1"),
                event("p1", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G"),
            ),
            olderToken = null,
            newerToken = null,
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("soon", vm.uiState.value.nextMatch?.matchId)
        assertEquals(listOf("soon", "later"), vm.uiState.value.upcomingIds)
    }

    @Test
    fun `without an upcoming match there is no next match`() = runTest(dispatcher) {
        schedule.pages[lckFilter to null] = SchedulePage(
            events = listOf(event("p1", "2026-10-01T10:00:00Z", MatchState.COMPLETED, "T1", "Gen.G")),
            olderToken = null,
            newerToken = null,
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.nextMatch)
    }

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
        const val T1_ID = "98767991853197861"
    }
}
