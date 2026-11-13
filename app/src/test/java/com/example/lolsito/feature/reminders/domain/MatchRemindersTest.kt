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
    fun `only matches starting after now and up to 48 hours ahead qualify`() {
        val events = listOf(
            event("past", start = now.minusSeconds(60)),
            event("now", start = now),
            event("soon", start = now.plusSeconds(60)),
            event("edge", start = now.plus(Duration.ofHours(48))),
            event("later", start = now.plus(Duration.ofHours(48)).plusSeconds(1)),
        )
        assertEquals(listOf("soon", "edge"), remindersFor(events, favorites, now).map { it.matchId })
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
    fun `a reminder is due 15 minutes before the start`() {
        val reminder = remindersFor(listOf(event()), favorites, now).single()
        assertEquals(now.plus(Duration.ofHours(2)).minus(Duration.ofMinutes(15)), reminder.triggerAt)
        assertEquals(Duration.ofMinutes(105), reminderDelay(reminder, now))
    }

    @Test
    fun `a reminder whose time already passed is due at once`() {
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
    fun `the title says how many minutes are left when it is shown`() {
        val start = now.plus(Duration.ofMinutes(15))
        assertEquals("GEN vs BLG starts in 15 minutes", reminderTitle("GEN vs BLG", start, now))
        assertEquals("GEN vs BLG starts in 15 minutes", reminderTitle("GEN vs BLG", start, now.minusSeconds(-20)))
        assertEquals("GEN vs BLG starts in 1 minute", reminderTitle("GEN vs BLG", now.plusSeconds(30), now))
        assertEquals("GEN vs BLG is starting", reminderTitle("GEN vs BLG", now, now))
        assertEquals("GEN vs BLG is starting", reminderTitle("GEN vs BLG", now.minusSeconds(120), now))
    }

    @Test
    fun `a reminder never carries a result`() {
        val scored = event(teams = listOf(team("Gen.G", "GEN", wins = 3, outcome = MatchOutcome.WIN), team("Bilibili Gaming", "BLG", wins = 2, outcome = MatchOutcome.LOSS)))
        val reminder = remindersFor(listOf(scored), favorites, now).single()
        val shown = listOf(reminder.teamsLabel, reminder.detail, reminderTitle(reminder.teamsLabel, reminder.startTime, reminder.triggerAt))
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
}
