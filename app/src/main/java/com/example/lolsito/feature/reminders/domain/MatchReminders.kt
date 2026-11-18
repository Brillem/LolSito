package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.involvesFavorite
import com.example.lolsito.feature.schedule.domain.isFavoriteIn
import com.example.lolsito.feature.teams.domain.teamScheduleLeagueIds
import java.time.Duration
import java.time.Instant

/**
 * How long before the official start of a match the first check of whether it has started is due, since
 * a match may start a little early. Fixed: there is no setting for it.
 */
val REMINDER_LEAD: Duration = Duration.ofMinutes(15)

/** How long to wait between two checks of whether a match has started. */
val START_CHECK_INTERVAL: Duration = Duration.ofMinutes(5)

/** How long after its official start a match that never went live stops being checked (postponed, cancelled). */
val START_CHECK_GIVE_UP: Duration = Duration.ofHours(3)

/** How far ahead a sync looks for matches to remind. */
val REMINDER_WINDOW: Duration = Duration.ofHours(48)

/**
 * The start-of-match reminder of one match: a notification shown once the match is really in progress,
 * whatever its official start time. It holds only what is known before the match starts (teams, league,
 * format), never a score or a result.
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
    /** When the first check of whether the match has started is due. */
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
 * Schedule star) or marked with "Remind me" ([markedMatchIds]) starting at most [REMINDER_WINDOW] after
 * [now]. A match past its official start still qualifies while it is unstarted and its checks have not
 * given up ([START_CHECK_GIVE_UP]), so a sync does not cancel the checks of a match that is running late.
 * A marked match also qualifies while in progress: its mark only goes once it is over (see [expiredMarks]),
 * so a sync made just after it went live never cancels the check that is about to notify it. Events
 * without a match have no identity to remind about.
 */
fun remindersFor(
    events: List<ScheduleEvent>,
    favoriteNames: Collection<String>,
    now: Instant,
    markedMatchIds: Set<String> = emptySet(),
): List<MatchReminder> {
    val horizon = now.plus(REMINDER_WINDOW)
    val stillChecked = now.minus(START_CHECK_GIVE_UP)
    return events
        .filter { event ->
            val marked = event.matchId?.let { it in markedMatchIds } == true
            val waiting = event.state == MatchState.UNSTARTED && event.startTime.isAfter(stillChecked) &&
                !event.startTime.isAfter(horizon) && (marked || event.involvesFavorite(favoriteNames))
            waiting || (marked && event.state == MatchState.IN_PROGRESS)
        }
        .mapNotNull { it.toReminder() }
        .distinctBy { it.matchId }
}

/**
 * The marks of [marked] to drop after a sync read [events] at [now]: those whose match is completed, and
 * those not in progress once [START_CHECK_GIVE_UP] has passed since their start (the schedule's, or the stored
 * one when the match is no longer among [events]), when their checks have given up anyway.
 */
fun expiredMarks(marked: List<MarkedMatch>, events: List<ScheduleEvent>, now: Instant): Set<String> {
    val byId = events.filter { it.matchId != null }.associateBy { it.matchId }
    return marked.filter { mark ->
        val event = byId[mark.matchId]
        when (event?.state) {
            MatchState.COMPLETED -> true
            MatchState.IN_PROGRESS -> false
            else -> !now.isBefore((event?.startTime ?: mark.startTime).plus(START_CHECK_GIVE_UP))
        }
    }.mapTo(HashSet()) { it.matchId }
}

private fun ScheduleEvent.toReminder(): MatchReminder? {
    val match = match ?: return null
    return MatchReminder(
        matchId = match.id,
        startTime = startTime,
        teamsLabel = reminderTeamsLabel(match.teams),
        detail = reminderDetail(leagueName, match.format),
        teamNames = match.teams.map { it.name },
    )
}

/** The teams of a reminder by code (by name when a code is missing), e.g. "GEN vs BLG". */
fun reminderTeamsLabel(teams: List<Team>): String =
    teams.joinToString(" vs ") { team -> team.code.trim().ifEmpty { team.name.trim() } }

/** The league and, when known, the format of a reminder, e.g. "LCK · Bo5". */
fun reminderDetail(leagueName: String, format: SeriesFormat?): String =
    listOfNotNull(leagueName.trim().takeIf { it.isNotEmpty() }, format?.label).joinToString(" · ")

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

/** How long to wait before the first check of [reminder]; zero when its time already passed. */
fun reminderDelay(reminder: MatchReminder, now: Instant): Duration =
    Duration.between(now, reminder.triggerAt).takeUnless { it.isNegative } ?: Duration.ZERO

/** The notification title, e.g. "GEN vs BLG has started". */
fun startedTitle(teamsLabel: String): String = "$teamsLabel has started"

/** What a check of whether a match has started leads to. */
enum class StartCheck {
    /** The match is in progress: show its notification. */
    NOTIFY,

    /** Not in progress yet, or the live feed could not be read: check again after [START_CHECK_INTERVAL]. */
    CHECK_AGAIN,

    /** [START_CHECK_GIVE_UP] has passed since the official start without the match going live: stop. */
    GIVE_UP,
}

/**
 * Decides a check of [reminder] at [now]. [liveMatchIds] are the matches in progress in the live feed
 * (see [matchIdsInProgress]), or null when the feed could not be read.
 */
fun startCheck(reminder: MatchReminder, liveMatchIds: Set<String>?, now: Instant): StartCheck = when {
    liveMatchIds != null && reminder.matchId in liveMatchIds -> StartCheck.NOTIFY
    now.isBefore(reminder.startTime.plus(START_CHECK_GIVE_UP)) -> StartCheck.CHECK_AGAIN
    else -> StartCheck.GIVE_UP
}

/** The ids of the matches being played among the [liveEvents] of the live feed. */
fun matchIdsInProgress(liveEvents: List<ScheduleEvent>): Set<String> =
    liveEvents.filter { it.state == MatchState.IN_PROGRESS }.mapNotNullTo(HashSet()) { it.match?.id }

/**
 * Checked at every check of the reminder: the match is still marked with "Remind me" ([markedMatchIds]), or
 * the bell is still on and one of the match's teams is still a favorite.
 */
fun MatchReminder.isStillWanted(
    remindersEnabled: Boolean,
    favoriteNames: Collection<String>,
    markedMatchIds: Collection<String> = emptySet(),
): Boolean =
    matchId in markedMatchIds ||
        remindersEnabled && teamNames.any { name -> Team(name, code = "", imageUrl = null, outcome = null, gameWins = null).isFavoriteIn(favoriteNames) }

/**
 * One schedule filter for all favorites and marked matches: each favorite's [teamScheduleLeagueIds] plus the
 * league of each marked match ([markedLeagueIds]), merged, sorted and without repeats.
 */
fun reminderLeagueIds(homeLeagueIds: List<String?>, markedLeagueIds: List<String> = emptyList()): List<String> =
    (homeLeagueIds.flatMap(::teamScheduleLeagueIds) + markedLeagueIds).distinct().sorted()
