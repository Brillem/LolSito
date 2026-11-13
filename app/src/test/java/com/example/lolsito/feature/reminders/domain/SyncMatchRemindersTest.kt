package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
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

    private class FakeScheduleRepository : ScheduleRepository {
        var events: List<ScheduleEvent> = emptyList()
        val requests = mutableListOf<Pair<List<String>, String?>>()
        var error: Throwable? = null

        override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
            requests += leagueIds to pageToken
            error?.let { throw it }
            return SchedulePage(events, olderToken = null, newerToken = null)
        }
    }

    private companion object {
        const val LCK = "98767991310872058"
        const val LPL = "98767991314006698"
        val GEN = FavoriteTeam("10", "gen-g", "Gen.G", "GEN", null)
        val T1 = FavoriteTeam("20", "t1", "T1", "T1", null)
    }
}
