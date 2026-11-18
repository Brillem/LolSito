package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.testutil.FakeReminderScheduler
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SyncMatchRemindersTest {

    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val preferences = FakeUserPreferences(matchRemindersEnabled = true, favoriteTeams = listOf(GEN, T1))
    private val scheduler = FakeReminderScheduler()
    private var leaguesError: Throwable? = null
    private val leagues = LeaguesRepository {
        leaguesError?.let { throw it }
        listOf(League(LCK, "lck", "LCK", "KOREA", null), League(LPL, "lpl", "LPL", "CHINA", null))
    }
    private val teams = FakeTeamsRepository()
    private val schedule = FakeScheduleRepository()

    private val sync = SyncMatchReminders(preferences, teams, leagues, schedule, scheduler, clock)

    private fun event(id: String, home: String, away: String, start: Instant = now.plus(Duration.ofHours(1))) = ScheduleEvent(
        startTime = start,
        state = MatchState.UNSTARTED,
        blockName = null,
        leagueName = "LCK",
        leagueSlug = "lck",
        match = Match(id, listOf(Team(home, home.take(3).uppercase(), null, null, null), Team(away, away.take(3).uppercase(), null, null, null)), SeriesFormat(SeriesType.BEST_OF, 3)),
    )

    init {
        teams.profiles[GEN.id] = profile(GEN, "LCK")
        teams.profiles[T1.id] = profile(T1, " lck ")
        schedule.events = listOf(event("m1", "Gen.G", "Hanwha Life"), event("m2", "DRX", "KT Rolster"))
    }

    @Test
    fun `asks the schedule once for the favorites' home leagues and the international leagues`() = runTest {
        teams.profiles[T1.id] = profile(T1, "LPL")

        sync()

        assertEquals(listOf((listOf(LCK, LPL) + InternationalLeagues.ids).sorted() to null), schedule.requests)
        assertEquals(listOf(GEN.id, T1.id), teams.requests)
    }

    @Test
    fun `a favorite whose home league is unknown still searches the international leagues`() = runTest {
        teams.profiles.remove(T1.id)
        teams.profiles[GEN.id] = profile(GEN, null)

        sync()

        assertEquals(listOf(InternationalLeagues.ids.sorted() to null), schedule.requests)
    }

    @Test
    fun `schedules a reminder for each match of a favorite`() = runTest {
        sync()

        assertEquals(listOf("m1"), scheduler.scheduledCalls.map { it.matchId })
        assertEquals(now.plus(Duration.ofMinutes(45)), scheduler.reminders.getValue("m1").triggerAt)
    }

    @Test
    fun `a second sync with nothing changed schedules nothing again`() = runTest {
        sync()
        sync()

        assertEquals(1, scheduler.scheduledCalls.size)
        assertTrue(scheduler.cancelledCalls.isEmpty())
    }

    @Test
    fun `a moved match is rescheduled`() = runTest {
        sync()
        schedule.events = listOf(event("m1", "Gen.G", "Hanwha Life", start = now.plus(Duration.ofHours(5))))

        sync()

        assertEquals(listOf("m1", "m1"), scheduler.scheduledCalls.map { it.matchId })
        assertEquals(now.plus(Duration.ofHours(5)), scheduler.reminders.getValue("m1").startTime)
    }

    @Test
    fun `a match that disappeared is cancelled`() = runTest {
        sync()
        schedule.events = emptyList()

        sync()

        assertEquals(listOf("m1"), scheduler.cancelledCalls)
        assertTrue(scheduler.reminders.isEmpty())
    }

    @Test
    fun `a team that stopped being a favorite has its reminders cancelled`() = runTest {
        sync()
        preferences.favoriteTeams = listOf(T1)

        sync()

        assertEquals(listOf("m1"), scheduler.cancelledCalls)
    }

    @Test
    fun `with the bell off everything is cancelled and nothing is requested`() = runTest {
        sync()
        preferences.matchRemindersEnabled = false
        teams.requests.clear()
        schedule.requests.clear()

        sync()

        assertTrue(scheduler.reminders.isEmpty())
        assertTrue(teams.requests.isEmpty())
        assertTrue(schedule.requests.isEmpty())
    }

    @Test
    fun `the bell never chosen counts as off`() = runTest {
        preferences.matchRemindersEnabled = null

        sync()

        assertTrue(scheduler.scheduledCalls.isEmpty())
        assertTrue(schedule.requests.isEmpty())
    }

    @Test
    fun `without favorites nothing is requested and everything is cancelled`() = runTest {
        sync()
        preferences.favoriteTeams = emptyList()
        schedule.requests.clear()

        sync()

        assertTrue(scheduler.reminders.isEmpty())
        assertTrue(schedule.requests.isEmpty())
    }

    @Test
    fun `a failed request fails the sync and leaves the reminders as they were`() = runTest {
        sync()
        for (failure in listOf<() -> Unit>({ leaguesError = IOException("leagues") }, { schedule.error = IOException("schedule") }, { teams.error = IOException("team") })) {
            leaguesError = null
            schedule.error = null
            teams.error = null
            failure()
            try {
                sync()
                fail("expected the sync to fail")
            } catch (e: IOException) {
                // Expected: the worker retries later.
            }
            assertEquals(listOf("m1"), scheduler.reminders.keys.toList())
            assertTrue(scheduler.cancelledCalls.isEmpty())
        }
    }

    @Test
    fun `a first page short of 48 hours asks for the newer page and reminds its matches too`() = runTest {
        schedule.newerToken = "n1"
        schedule.newerPages["n1"] = SchedulePage(
            listOf(event("m3", "T1", "DRX", start = hoursAhead(30)), event("m4", "Gen.G", "DRX", start = hoursAhead(50))),
            olderToken = null,
            newerToken = "n2",
        )

        sync()

        assertEquals(listOf(null, "n1"), schedule.requests.map { it.second })
        assertEquals("every page asks for the same leagues", 1, schedule.requests.map { it.first }.distinct().size)
        assertEquals(listOf("m1", "m3"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `asks for at most 2 newer pages`() = runTest {
        schedule.newerToken = "n1"
        schedule.newerPages["n1"] = SchedulePage(listOf(event("m3", "T1", "DRX", start = hoursAhead(2))), null, "n2")
        schedule.newerPages["n2"] = SchedulePage(listOf(event("m4", "Gen.G", "DRX", start = hoursAhead(3))), null, "n3")
        schedule.newerPages["n3"] = SchedulePage(listOf(event("m5", "T1", "KT Rolster", start = hoursAhead(4))), null, "n4")

        sync()

        assertEquals(listOf(null, "n1", "n2"), schedule.requests.map { it.second })
        assertEquals(listOf("m1", "m3", "m4"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `a first page reaching 48 hours asks for no newer page`() = runTest {
        schedule.events = listOf(event("m1", "Gen.G", "Hanwha Life"), event("m2", "DRX", "KT Rolster", start = hoursAhead(48)))
        schedule.newerToken = "n1"

        sync()

        assertEquals(listOf<String?>(null), schedule.requests.map { it.second })
        assertEquals(listOf("m1"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `a failed newer page fails the sync and leaves the reminders as they were`() = runTest {
        sync()
        schedule.events = listOf(event("m9", "T1", "DRX"))
        schedule.newerToken = "n1"
        schedule.failingToken = "n1"

        try {
            sync()
            fail("expected the sync to fail")
        } catch (e: IOException) {
            // Expected: the worker retries later.
        }

        assertEquals(listOf("m1"), scheduler.reminders.keys.toList())
        assertEquals(listOf("m1"), scheduler.scheduledCalls.map { it.matchId })
        assertTrue(scheduler.cancelledCalls.isEmpty())
    }

    @Test
    fun `a page reaches the window when it has no newer page or an event 48 hours ahead or later`() {
        val soon = event("m1", "Gen.G", "DRX")
        val atHorizon = event("m2", "Gen.G", "DRX", start = hoursAhead(48))
        val justShort = event("m3", "Gen.G", "DRX", start = hoursAhead(48).minusMillis(1))

        assertTrue(reachesReminderWindow(SchedulePage(listOf(soon), null, newerToken = null), now))
        assertTrue(reachesReminderWindow(SchedulePage(emptyList(), null, newerToken = null), now))
        assertTrue(reachesReminderWindow(SchedulePage(listOf(soon, atHorizon), null, newerToken = "n1"), now))
        assertFalse(reachesReminderWindow(SchedulePage(listOf(soon, justShort), null, newerToken = "n1"), now))
        assertFalse(reachesReminderWindow(SchedulePage(emptyList(), null, newerToken = "n1"), now))
    }

    // Matches marked with "Remind me".

    private fun mark(id: String, league: String = LEC, start: Instant = now.plus(Duration.ofHours(1))) =
        MarkedMatch(id, league, start, "DRX vs KT", "LCK · Bo3")

    @Test
    fun `a marked match is reminded with the bell off and no favorites, asking only for its league`() = runTest {
        preferences.matchRemindersEnabled = false
        preferences.favoriteTeams = emptyList()
        preferences.markedMatches = listOf(mark("m2"))

        sync()

        assertEquals(listOf(listOf(LEC) to null), schedule.requests)
        assertTrue(teams.requests.isEmpty())
        assertEquals(listOf("m2"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `marked leagues are added to the favorites' leagues and both kinds of match are reminded`() = runTest {
        preferences.markedMatches = listOf(mark("m2"))

        sync()

        assertEquals(listOf((listOf(LCK, LEC) + InternationalLeagues.ids).distinct().sorted() to null), schedule.requests)
        assertEquals(listOf("m1", "m2"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `a marked match beyond 48 hours stays marked and is scheduled once a later sync sees it in the window`() = runTest {
        preferences.favoriteTeams = emptyList()
        val later = now.plus(Duration.ofHours(60))
        preferences.markedMatches = listOf(mark("m3", start = later))
        schedule.events = listOf(event("m3", "DRX", "KT Rolster", start = later))

        sync()

        assertTrue(scheduler.scheduledCalls.isEmpty())
        assertEquals(listOf("m3"), preferences.markedMatches.orEmpty().map { it.matchId })

        val laterSync = SyncMatchReminders(preferences, teams, leagues, schedule, scheduler, Clock.fixed(later.minus(Duration.ofHours(10)), ZoneOffset.UTC))
        laterSync()

        assertEquals(listOf("m3"), scheduler.scheduledCalls.map { it.matchId })
    }

    @Test
    fun `a mark is removed once its match is completed or gone past the give up time, and its reminder cancelled`() = runTest {
        preferences.favoriteTeams = emptyList()
        preferences.markedMatches = listOf(mark("m2"), mark("gone", start = now.minus(START_CHECK_GIVE_UP)), mark("kept", start = now.plus(Duration.ofDays(4))))
        sync()
        assertEquals(listOf("m2"), scheduler.reminders.keys.toList())

        schedule.events = listOf(event("m2", "DRX", "KT Rolster").copy(state = MatchState.COMPLETED))
        sync()

        assertEquals(listOf("kept"), preferences.markedMatches.orEmpty().map { it.matchId })
        assertEquals(listOf("m2"), scheduler.cancelledCalls)
    }

    @Test
    fun `an unmarked match has its reminder cancelled`() = runTest {
        preferences.favoriteTeams = emptyList()
        preferences.markedMatches = listOf(mark("m2"))
        sync()

        preferences.markedMatches = emptyList()
        sync()

        assertEquals(listOf("m2"), scheduler.cancelledCalls)
        assertTrue(scheduler.reminders.isEmpty())
    }

    @Test
    fun `a mark that cannot be removed does not fail the sync`() = runTest {
        preferences.favoriteTeams = emptyList()
        preferences.markedMatches = listOf(mark("gone", start = now.minus(START_CHECK_GIVE_UP)))
        preferences.writeFails = true

        sync()

        assertEquals(1, preferences.markedMatches.orEmpty().size)
    }

    private fun hoursAhead(hours: Long): Instant = now.plus(Duration.ofHours(hours))

    private fun profile(team: FavoriteTeam, homeLeague: String?) =
        TeamProfile(team.id, team.slug, team.name, team.code, null, homeLeague, null, emptyList())

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

    /** The first page holds [events] and points to [newerToken]; [newerPages] answer by their token. */
    private class FakeScheduleRepository : ScheduleRepository {
        var events: List<ScheduleEvent> = emptyList()
        var newerToken: String? = null
        val newerPages = mutableMapOf<String, SchedulePage>()
        val requests = mutableListOf<Pair<List<String>, String?>>()
        var error: Throwable? = null

        /** Fails only the request of this page token. */
        var failingToken: String? = null

        override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
            requests += leagueIds to pageToken
            error?.let { throw it }
            if (pageToken == null) return SchedulePage(events, olderToken = null, newerToken = newerToken)
            if (pageToken == failingToken) throw IOException("page $pageToken")
            return newerPages.getValue(pageToken)
        }
    }

    private companion object {
        const val LCK = "98767991310872058"
        const val LPL = "98767991314006698"
        const val LEC = "98767991302996019"
        val GEN = FavoriteTeam("10", "gen-g", "Gen.G", "GEN", null)
        val T1 = FavoriteTeam("20", "t1", "T1", "T1", null)
    }
}
