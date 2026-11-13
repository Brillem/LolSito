package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.involvesFavorite
import com.example.lolsito.feature.schedule.domain.isFavoriteIn
import com.example.lolsito.feature.teams.domain.teamScheduleLeagueIds
import java.time.Duration
import java.time.Instant

/** How long before a match starts its reminder is due. Fixed: there is no setting for it. */
val REMINDER_LEAD: Duration = Duration.ofMinutes(15)

/** How far ahead a sync looks for matches to remind. */
val REMINDER_WINDOW: Duration = Duration.ofHours(48)

/**
 * The start-of-match reminder of one match. It holds only what is known before the match starts
 * (teams, league, format), never a score or a result.
 */
data class MatchReminder(
    val matchId: String,
    val startTime: Instant,
    /** The teams by code, e.g. "GEN vs BLG". */
    val teamsLabel: String,
    /** The league and, when known, the format, e.g. "LCK · Bo5". */
    val detail: String,
    /** The names of the match's teams, to check when it is shown that one is still a favorite. */
    val teamNames: List<String>,
) {
    val triggerAt: Instant get() = startTime.minus(REMINDER_LEAD)

    /** Changes whenever the start time or anything shown changes, so such a match is scheduled again. */
    val version: String
        get() = listOf(startTime.toEpochMilli().toString(), teamsLabel, detail, teamNames.joinToString(",")).joinToString("|")
}

/** A reminder the scheduler holds: its match and the [MatchReminder.version] it was scheduled with. */
data class ScheduledReminder(val matchId: String, val version: String)

/** What a sync changes: reminders to (re)schedule, and the match ids whose reminders to cancel. */
data class ReminderPlan(val toSchedule: List<MatchReminder>, val toCancel: List<String>)

/**
 * The reminders [events] call for: one per unstarted match of a favorite (recognized by name, like the
 * Schedule star) starting after [now] and at most [REMINDER_WINDOW] later. Events without a match have
 * no identity to remind about.
 */
fun remindersFor(events: List<ScheduleEvent>, favoriteNames: Collection<String>, now: Instant): List<MatchReminder> {
    val horizon = now.plus(REMINDER_WINDOW)
    return events
        .filter { it.state == MatchState.UNSTARTED && it.startTime.isAfter(now) && !it.startTime.isAfter(horizon) }
        .filter { it.involvesFavorite(favoriteNames) }
        .mapNotNull { it.toReminder() }
        .distinctBy { it.matchId }
}

private fun ScheduleEvent.toReminder(): MatchReminder? {
    val match = match ?: return null
    return MatchReminder(
        matchId = match.id,
        startTime = startTime,
        teamsLabel = match.teams.joinToString(" vs ") { team -> team.code.trim().ifEmpty { team.name.trim() } },
        detail = listOfNotNull(leagueName.trim().takeIf { it.isNotEmpty() }, match.format?.label).joinToString(" · "),
        teamNames = match.teams.map { it.name },
    )
}

/**
 * Schedules every wanted reminder not scheduled yet or scheduled with another version (the start time
 * or the text changed), and cancels every scheduled one no longer wanted (the match disappeared, started,
 * or no longer involves a favorite). Unchanged reminders are left alone.
 */
fun planReminders(wanted: List<MatchReminder>, scheduled: Collection<ScheduledReminder>): ReminderPlan {
    val scheduledVersions = scheduled.associate { it.matchId to it.version }
    val wantedIds = wanted.mapTo(HashSet()) { it.matchId }
    return ReminderPlan(
        toSchedule = wanted.filter { scheduledVersions[it.matchId] != it.version },
        toCancel = scheduled.map { it.matchId }.filterNot { it in wantedIds }.distinct(),
    )
}

/** How long to wait before showing [reminder]; zero when its time already passed. */
fun reminderDelay(reminder: MatchReminder, now: Instant): Duration =
    Duration.between(now, reminder.triggerAt).takeUnless { it.isNegative } ?: Duration.ZERO

/**
 * The notification title, from the minutes left when it is shown (rounded up), since Android may
 * show it a little late: "GEN vs BLG starts in 15 minutes", or "is starting" once the time has come.
 */
fun reminderTitle(teamsLabel: String, startTime: Instant, now: Instant): String {
    val secondsLeft = Duration.between(now, startTime).seconds
    if (secondsLeft <= 0) return "$teamsLabel is starting"
    val minutes = (secondsLeft + 59) / 60
    return "$teamsLabel starts in $minutes ${if (minutes == 1L) "minute" else "minutes"}"
}

/** Checked when the reminder is due: the bell is still on and one of the match's teams is still a favorite. */
fun MatchReminder.isStillWanted(remindersEnabled: Boolean, favoriteNames: Collection<String>): Boolean =
    remindersEnabled && teamNames.any { name -> Team(name, code = "", imageUrl = null, outcome = null, gameWins = null).isFavoriteIn(favoriteNames) }

/** One schedule filter for all favorites: each one's [teamScheduleLeagueIds], merged, sorted and without repeats. */
fun reminderLeagueIds(homeLeagueIds: List<String?>): List<String> =
    homeLeagueIds.flatMap(::teamScheduleLeagueIds).distinct().sorted()
