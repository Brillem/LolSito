package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import com.example.lolsito.feature.schedule.domain.mergeEvents
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.feature.teams.domain.homeLeagueId
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant

/**
 * Brings the scheduled reminders in line with the bell, the favorites, the matches marked with "Remind me"
 * and the schedule.
 *
 * The favorites count only with the bell on. With no favorite counting and no marked match, every reminder
 * is cancelled and nothing is requested. Otherwise each favorite's home league is found as the team profile
 * does (getTeams gives the league name, matched against the leagues list), the international leagues are
 * added (only when a favorite counts), so is the league of each marked match, and the schedule is asked for
 * all of them: the first page and, while it does not reach [REMINDER_WINDOW] ahead
 * (see [reachesReminderWindow]), up to [MAX_NEWER_PAGES] newer pages; [remindersFor] picks the matches and [planReminders] decides
 * what to schedule and cancel. Marked matches are reminded like a favorite's, whatever the bell; once a sync
 * sees one over ([expiredMarks]) its mark is removed, so the list does not grow forever.
 *
 * Any failure (preferences, leagues, a team, the schedule) is thrown before anything changes, so the
 * reminders stay as they were and the caller can retry. Removing expired marks is best effort: a failure
 * there keeps them for the next sync.
 */
class SyncMatchReminders(
    private val preferences: UserPreferences,
    private val teamsRepository: TeamsRepository,
    private val leaguesRepository: LeaguesRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val wanted = wantedReminders()
        val plan = planReminders(wanted.reminders, scheduler.scheduled())
        plan.toCancel.forEach(scheduler::cancel)
        plan.toSchedule.forEach(scheduler::schedule)
        if (wanted.expiredMarks.isNotEmpty()) removeMarks(wanted.expiredMarks)
    }

    private class Wanted(val reminders: List<MatchReminder>, val expiredMarks: Set<String>)

    private suspend fun wantedReminders(): Wanted {
        val marked = preferences.getMarkedMatches().orEmpty()
        val enabled = preferences.getMatchRemindersEnabled() ?: false
        val favorites = if (enabled) preferences.getFavoriteTeams().orEmpty() else emptyList()
        if (favorites.isEmpty() && marked.isEmpty()) return Wanted(emptyList(), emptySet())
        val homeLeagueIds = if (favorites.isEmpty()) {
            emptyList()
        } else {
            val leagues = leaguesRepository.getLeagues()
            favorites.map { favorite -> teamsRepository.getTeam(favorite.id)?.let { homeLeagueId(leagues, it.homeLeagueName) } }
        }
        val now = clock.instant()
        val events = upcomingEvents(reminderLeagueIds(homeLeagueIds, marked.map { it.leagueId }), now)
        val expired = expiredMarks(marked, events, now)
        val markedIds = marked.mapTo(HashSet()) { it.matchId } - expired
        return Wanted(remindersFor(events, favoriteNamesOf(favorites), now, markedIds), expired)
    }

    private suspend fun removeMarks(matchIds: Set<String>) {
        try {
            preferences.removeMarkedMatches(matchIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept for the next sync to remove.
        }
    }

    /** The first schedule page plus, while they do not reach the window, up to [MAX_NEWER_PAGES] newer ones. */
    private suspend fun upcomingEvents(leagueIds: List<String>, now: Instant): List<ScheduleEvent> {
        var page = scheduleRepository.getSchedule(leagueIds, pageToken = null)
        var events = page.events
        var newerPages = 0
        while (newerPages < MAX_NEWER_PAGES && !reachesReminderWindow(page, now)) {
            page = scheduleRepository.getSchedule(leagueIds, page.newerToken)
            events = mergeEvents(events, page.events)
            newerPages++
        }
        return events
    }
}

/** How many pages beyond the first a sync may ask for to reach [REMINDER_WINDOW]. */
internal const val MAX_NEWER_PAGES = 2

/**
 * Whether nothing newer than [page] can matter to a sync at [now]: the page has no newer page, or one of
 * its events starts at or after now + [REMINDER_WINDOW] (pages run in time order, so every later page
 * starts beyond the window too).
 */
internal fun reachesReminderWindow(page: SchedulePage, now: Instant): Boolean {
    if (page.newerToken == null) return true
    val horizon = now.plus(REMINDER_WINDOW)
    return page.events.any { !it.startTime.isBefore(horizon) }
}
