package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.teams.domain.InternationalLeagues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MatchRemindersTest {

    private val now = Instant.parse("2026-11-12T10:00:00Z")

    private fun event(
        matchId: String? = "m1",
        start: Instant = now.plus(Duration.ofHours(2)),
        state: MatchState = MatchState.UNSTARTED,
        teams: List<Team> = listOf(team("Gen.G", "GEN"), team("Bilibili Gaming", "BLG")),
        league: String = "LCK",
        format: SeriesFormat? = SeriesFormat(SeriesType.BEST_OF, 5),
    ) = ScheduleEvent(
        startTime = start,
        state = state,
        blockName = "Finals",
        leagueName = league,
        leagueSlug = league.lowercase(),
        match = matchId?.let { Match(it, teams, format) },
    )

    private fun team(name: String, code: String, wins: Int? = null, outcome: MatchOutcome? = null) =
        Team(name, code, null, outcome, wins)

    private val favorites = listOf("Gen.G")

    // Which matches qualify.

    @Test
    fun `an unstarted match of a favorite within 48 hours gets a reminder`() {
        val reminders = remindersFor(listOf(event()), favorites, now)
        assertEquals(listOf("m1"), reminders.map { it.matchId })
        assertEquals(now.plus(Duration.ofHours(2)), reminders.single().startTime)
    }

    @Test
    fun `a favorite is recognized by name, trimmed and ignoring case, like the Schedule star`() {
        val reminders = remindersFor(listOf(event()), listOf("  gen.g "), now)
        assertEquals(listOf("m1"), reminders.map { it.matchId })
    }

    @Test
    fun `a match without a favorite gets no reminder`() {
        assertTrue(remindersFor(listOf(event()), listOf("T1"), now).isEmpty())
        assertTrue(remindersFor(listOf(event()), emptyList(), now).isEmpty())
    }

    @Test
    fun `live, finished and unknown matches get no reminder`() {
        val events = listOf(
            event("live", state = MatchState.IN_PROGRESS),
            event("done", state = MatchState.COMPLETED),
            event("unknown", state = MatchState.UNKNOWN),
        )
        assertTrue(remindersFor(events, favorites, now).isEmpty())
    }

    @Test
    fun `unstarted matches qualify from 3 hours after their official start up to 48 hours ahead`() {
        val events = listOf(
            event("gone", start = now.minus(Duration.ofHours(3))),
            event("late", start = now.minus(Duration.ofHours(3)).plusSeconds(1)),
            event("past", start = now.minusSeconds(60)),
            event("soon", start = now.plusSeconds(60)),
            event("edge", start = now.plus(Duration.ofHours(48))),
            event("later", start = now.plus(Duration.ofHours(48)).plusSeconds(1)),
        )
        assertEquals(listOf("late", "past", "soon", "edge"), remindersFor(events, favorites, now).map { it.matchId })
    }

    @Test
    fun `an event without a match has no identity and gets no reminder`() {
        assertTrue(remindersFor(listOf(event(matchId = null)), favorites, now).isEmpty())
    }

    @Test
    fun `a match listed twice gets one reminder`() {
        assertEquals(1, remindersFor(listOf(event(), event()), favorites, now).size)
    }

    // When it is due.

    @Test
    fun `the first check is due 15 minutes before the official start`() {
        val reminder = remindersFor(listOf(event()), favorites, now).single()
        assertEquals(now.plus(Duration.ofHours(2)).minus(Duration.ofMinutes(15)), reminder.triggerAt)
        assertEquals(Duration.ofMinutes(105), reminderDelay(reminder, now))
    }

    @Test
    fun `a first check whose time already passed is due at once`() {
        val reminder = remindersFor(listOf(event(start = now.plus(Duration.ofMinutes(5)))), favorites, now).single()
        assertEquals(Duration.ZERO, reminderDelay(reminder, now))
    }

    // What it says.

    @Test
    fun `the reminder names the teams by code, the league and the format`() {
        val reminder = remindersFor(listOf(event()), favorites, now).single()
        assertEquals("GEN vs BLG", reminder.teamsLabel)
        assertEquals("LCK · Bo5", reminder.detail)
        assertEquals(listOf("Gen.G", "Bilibili Gaming"), reminder.teamNames)
    }

    @Test
    fun `without a known format the detail is the league alone`() {
        val reminder = remindersFor(listOf(event(format = null)), favorites, now).single()
        assertEquals("LCK", reminder.detail)
    }

    @Test
    fun `a team without a code is named by its name`() {
        val reminder = remindersFor(listOf(event(teams = listOf(team("Gen.G", " "), team("TBD", "TBD")))), favorites, now).single()
        assertEquals("Gen.G vs TBD", reminder.teamsLabel)
    }

    @Test
    fun `the title says the match has started`() {
        assertEquals("GEN vs BLG has started", startedTitle("GEN vs BLG"))
    }

    // Whether it has started.

    private val reminder = remindersFor(listOf(event()), favorites, now).single()
    private val giveUpAt = reminder.startTime.plus(Duration.ofHours(3))

    @Test
    fun `a match in progress in the live feed is notified`() {
        assertEquals(StartCheck.NOTIFY, startCheck(reminder, liveMatchIds = setOf("other", "m1"), now = now))
    }

    @Test
    fun `a match not live yet is checked again, also after its official start`() {
        assertEquals(StartCheck.CHECK_AGAIN, startCheck(reminder, liveMatchIds = emptySet(), now = now))
        assertEquals(StartCheck.CHECK_AGAIN, startCheck(reminder, liveMatchIds = setOf("other"), now = reminder.startTime.plusSeconds(3600)))
        assertEquals(StartCheck.CHECK_AGAIN, startCheck(reminder, liveMatchIds = emptySet(), now = giveUpAt.minusSeconds(1)))
    }

    @Test
    fun `a live feed that could not be read is asked again without notifying`() {
        assertEquals(StartCheck.CHECK_AGAIN, startCheck(reminder, liveMatchIds = null, now = now))
    }

    @Test
    fun `3 hours after the official start the checks give up`() {
        assertEquals(StartCheck.GIVE_UP, startCheck(reminder, liveMatchIds = emptySet(), now = giveUpAt))
        assertEquals(StartCheck.GIVE_UP, startCheck(reminder, liveMatchIds = null, now = giveUpAt.plusSeconds(60)))
    }

    @Test
    fun `a match found live at the last check is still notified`() {
        assertEquals(StartCheck.NOTIFY, startCheck(reminder, liveMatchIds = setOf("m1"), now = giveUpAt))
    }

    @Test
    fun `checks are 5 minutes apart`() {
        assertEquals(Duration.ofMinutes(5), START_CHECK_INTERVAL)
    }

    @Test
    fun `only matches in progress count as started in the live feed`() {
        val live = listOf(
            event("playing", state = MatchState.IN_PROGRESS),
            event("waiting", state = MatchState.UNSTARTED),
            event("done", state = MatchState.COMPLETED),
            event(matchId = null, state = MatchState.IN_PROGRESS),
        )
        assertEquals(setOf("playing"), matchIdsInProgress(live))
    }

    @Test
    fun `a reminder never carries a result`() {
        val scored = event(teams = listOf(team("Gen.G", "GEN", wins = 3, outcome = MatchOutcome.WIN), team("Bilibili Gaming", "BLG", wins = 2, outcome = MatchOutcome.LOSS)))
        val reminder = remindersFor(listOf(scored), favorites, now).single()
        val shown = listOf(reminder.teamsLabel, reminder.detail, startedTitle(reminder.teamsLabel))
        shown.forEach { text ->
            assertFalse(text, text.contains("3"))
            assertFalse(text, text.contains("2"))
            assertFalse(text, text.contains("win", ignoreCase = true))
        }
    }

    // What to schedule and what to cancel.

    private fun reminder(id: String, start: Instant = now.plus(Duration.ofHours(2))) =
        remindersFor(listOf(event(id, start = start)), favorites, now).single()

    private fun MatchReminder.asScheduled() = ScheduledReminder(matchId, version)

    @Test
    fun `new matches are scheduled and nothing is cancelled`() {
        val a = reminder("a")
        assertEquals(ReminderPlan(toSchedule = listOf(a), toCancel = emptyList()), planReminders(listOf(a), emptyList()))
    }

    @Test
    fun `an unchanged reminder is left alone`() {
        val a = reminder("a")
        assertEquals(ReminderPlan(emptyList(), emptyList()), planReminders(listOf(a), listOf(a.asScheduled())))
    }

    @Test
    fun `a match whose start time changed is scheduled again`() {
        val before = reminder("a")
        val moved = reminder("a", start = now.plus(Duration.ofHours(3)))
        assertTrue(before.version != moved.version)
        assertEquals(ReminderPlan(listOf(moved), emptyList()), planReminders(listOf(moved), listOf(before.asScheduled())))
    }

    @Test
    fun `a match whose text changed is scheduled again`() {
        val before = reminder("a")
        val renamed = before.copy(teamsLabel = "GEN vs T1")
        assertEquals(ReminderPlan(listOf(renamed), emptyList()), planReminders(listOf(renamed), listOf(before.asScheduled())))
    }

    @Test
    fun `a match that disappeared or stopped qualifying is cancelled`() {
        val a = reminder("a")
        val gone = reminder("gone")
        assertEquals(ReminderPlan(emptyList(), listOf("gone")), planReminders(listOf(a), listOf(a.asScheduled(), gone.asScheduled())))
    }

    @Test
    fun `wanting nothing cancels everything scheduled`() {
        val scheduled = listOf(reminder("a").asScheduled(), reminder("b").asScheduled())
        assertEquals(ReminderPlan(emptyList(), listOf("a", "b")), planReminders(emptyList(), scheduled))
    }

    // Checked again when it is shown.

    @Test
    fun `a reminder is still wanted only while the bell is on and one of its teams is a favorite`() {
        val a = reminder("a")
        assertTrue(a.isStillWanted(remindersEnabled = true, favoriteNames = listOf("GEN.G")))
        assertFalse(a.isStillWanted(remindersEnabled = false, favoriteNames = listOf("Gen.G")))
        assertFalse(a.isStillWanted(remindersEnabled = true, favoriteNames = listOf("T1")))
    }

    // What it queries.

    @Test
    fun `the schedule filter is every favorite's home league plus the international leagues, once each`() {
        assertEquals(
            (listOf("lck", "lpl") + InternationalLeagues.ids).sorted(),
            reminderLeagueIds(listOf("lpl", null, "lck", "lpl")),
        )
        assertEquals(InternationalLeagues.ids.sorted(), reminderLeagueIds(listOf(null)))
    }

    // Matches marked with "Remind me".

    @Test
    fun `a marked match gets a reminder whatever its teams, like a favorite's match`() {
        val reminders = remindersFor(listOf(event("m1"), event("m2")), emptyList(), now, markedMatchIds = setOf("m2"))
        assertEquals(listOf("m2"), reminders.map { it.matchId })
        assertEquals(remindersFor(listOf(event("m2")), favorites, now), reminders)
    }

    @Test
    fun `a marked match qualifies only within the same window as a favorite's match`() {
        val events = listOf(
            event("far", start = now.plus(REMINDER_WINDOW).plusSeconds(1)),
            event("gaveUp", start = now.minus(START_CHECK_GIVE_UP)),
            event("done", state = MatchState.COMPLETED),
        )
        assertTrue(remindersFor(events, emptyList(), now, markedMatchIds = setOf("far", "gaveUp", "done")).isEmpty())
    }

    @Test
    fun `a marked match in progress keeps its reminder, so a sync never cancels it before its check notifies`() {
        val live = event("live", start = now.minus(Duration.ofMinutes(5)), state = MatchState.IN_PROGRESS)
        assertEquals(listOf("live"), remindersFor(listOf(live), emptyList(), now, markedMatchIds = setOf("live")).map { it.matchId })
        assertTrue(remindersFor(listOf(live), favorites, now).isEmpty())
    }

    @Test
    fun `a marked reminder is still wanted with the bell off and no favorite, and not once unmarked`() {
        val a = reminder("a")
        assertTrue(a.isStillWanted(remindersEnabled = false, favoriteNames = emptyList(), markedMatchIds = setOf("a")))
        assertFalse(a.isStillWanted(remindersEnabled = false, favoriteNames = emptyList(), markedMatchIds = setOf("b")))
    }

    @Test
    fun `a mark expires once its match is completed or past the give up time without being live`() {
        fun mark(id: String, start: Instant) =
            com.example.lolsito.core.preferences.MarkedMatch(id, "lck", start, "GEN vs BLG", "LCK · Bo5")
        val longAgo = now.minus(START_CHECK_GIVE_UP)
        val marks = listOf(
            mark("done", now.minus(Duration.ofHours(1))),
            mark("live", longAgo),
            mark("upcoming", now.plus(Duration.ofDays(5))),
            mark("vanished", longAgo),
            mark("vanishedRecently", now.minus(Duration.ofHours(1))),
            mark("postponedLate", now.minus(Duration.ofHours(1))),
            mark("delayedNow", longAgo),
        )
        val events = listOf(
            event("done", start = now.minus(Duration.ofHours(1)), state = MatchState.COMPLETED),
            event("live", start = longAgo, state = MatchState.IN_PROGRESS),
            event("postponedLate", start = longAgo, state = MatchState.UNSTARTED),
            event("delayedNow", start = now.plus(Duration.ofHours(1)), state = MatchState.UNSTARTED),
        )
        assertEquals(setOf("done", "vanished", "postponedLate"), expiredMarks(marks, events, now))
    }

    @Test
    fun `marked matches add their leagues to the favorites' schedule filter`() {
        assertEquals(
            (listOf("lck", "lec") + InternationalLeagues.ids).distinct().sorted(),
            reminderLeagueIds(listOf("lck"), markedLeagueIds = listOf("lec", "lck")),
        )
        assertEquals(listOf("lec"), reminderLeagueIds(emptyList(), markedLeagueIds = listOf("lec")))
    }
}
