package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.testutil.FakeReminderScheduler
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.CancellationException
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

class CheckMatchStartTest {

    private val start = Instant.parse("2026-11-12T10:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val preferences = FakeUserPreferences(
        matchRemindersEnabled = true,
        favoriteTeams = listOf(FavoriteTeam("10", "gen-g", "Gen.G", "GEN", null)),
    )
    private val scheduler = FakeReminderScheduler()
    private var live: List<ScheduleEvent> = emptyList()
    private var liveError: Throwable? = null
    private var liveRequests = 0
    private val liveMatches = object : LiveMatchesRepository {
        override suspend fun getLiveMatches(): List<ScheduleEvent> {
            liveRequests++
            liveError?.let { throw it }
            return live
        }
    }
    private val check = CheckMatchStart(preferences, liveMatches, scheduler, clock)

    private val reminder = MatchReminder("m1", start, "GEN vs BLG", "LCK · Bo5", listOf("Gen.G", "Bilibili Gaming"))

    private fun liveEvent(matchId: String, state: MatchState = MatchState.IN_PROGRESS) = ScheduleEvent(
        startTime = start,
        state = state,
        blockName = null,
        leagueName = "LCK",
        leagueSlug = "lck",
        match = Match(matchId, listOf(Team("Gen.G", "GEN", null, null, null)), null),
    )

    @Test
    fun `a match in progress is to be notified and is not checked again`() = runTest {
        live = listOf(liveEvent("m1"))

        assertTrue(check(reminder))
        assertTrue(scheduler.checkAgainCalls.isEmpty())
    }

    @Test
    fun `a match that has not started is checked again without notifying`() = runTest {
        live = listOf(liveEvent("other"), liveEvent("m1", MatchState.UNSTARTED))
        now = start.plus(Duration.ofMinutes(40))

        assertFalse(check(reminder))
        assertEquals(listOf(reminder), scheduler.checkAgainCalls)
    }

    @Test
    fun `a live feed that fails is asked again without notifying`() = runTest {
        liveError = IOException("no network")

        assertFalse(check(reminder))
        assertEquals(listOf(reminder), scheduler.checkAgainCalls)
    }

    @Test
    fun `3 hours after the official start nothing is notified or checked again`() = runTest {
        now = start.plus(Duration.ofHours(3))

        assertFalse(check(reminder))
        assertTrue(scheduler.checkAgainCalls.isEmpty())
    }

    @Test
    fun `with the bell off the checks stop without asking the live feed`() = runTest {
        preferences.matchRemindersEnabled = false
        live = listOf(liveEvent("m1"))

        assertFalse(check(reminder))
        assertTrue(scheduler.checkAgainCalls.isEmpty())
        assertEquals(0, liveRequests)
    }

    @Test
    fun `once none of its teams is a favorite the checks stop`() = runTest {
        preferences.favoriteTeams = listOf(FavoriteTeam("20", "t1", "T1", "T1", null))
        live = listOf(liveEvent("m1"))

        assertFalse(check(reminder))
        assertTrue(scheduler.checkAgainCalls.isEmpty())
    }

    @Test
    fun `unreadable preferences are no reason to drop the reminder`() = runTest {
        preferences.readFails = true
        live = listOf(liveEvent("m1"))

        assertTrue(check(reminder))
    }

    @Test
    fun `a cancelled check is not swallowed`() = runTest {
        liveError = CancellationException("stopped")

        try {
            check(reminder)
            fail("expected the cancellation")
        } catch (e: CancellationException) {
            assertTrue(scheduler.checkAgainCalls.isEmpty())
        }
    }
}
