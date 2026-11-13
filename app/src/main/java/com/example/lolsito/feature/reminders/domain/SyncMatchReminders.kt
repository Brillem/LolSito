package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.feature.teams.domain.homeLeagueId
import java.time.Clock

/**
 * Brings the scheduled reminders in line with the bell, the favorites and the schedule.
 *
 * With the bell off (or never turned on) or no favorites, every reminder is cancelled and nothing is
 * requested. Otherwise each favorite's home league is found as the team profile does (getTeams gives
 * the league name, matched against the leagues list), the international leagues are added, and the
 * schedule is asked once for all of them; [remindersFor] picks the matches and [planReminders] decides
 * what to schedule and cancel.
 *
 * Any failure (preferences, leagues, a team, the schedule) is thrown before anything changes, so the
 * reminders stay as they were and the caller can retry.
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
        val plan = planReminders(wantedReminders(), scheduler.scheduled())
        plan.toCancel.forEach(scheduler::cancel)
        plan.toSchedule.forEach(scheduler::schedule)
    }

    private suspend fun wantedReminders(): List<MatchReminder> {
        val enabled = preferences.getMatchRemindersEnabled() ?: false
        if (!enabled) return emptyList()
        val favorites = preferences.getFavoriteTeams().orEmpty()
        if (favorites.isEmpty()) return emptyList()
        val leagues = leaguesRepository.getLeagues()
        val homeLeagueIds = favorites.map { favorite ->
            teamsRepository.getTeam(favorite.id)?.let { homeLeagueId(leagues, it.homeLeagueName) }
        }
        val page = scheduleRepository.getSchedule(reminderLeagueIds(homeLeagueIds), pageToken = null)
        return remindersFor(page.events, favoriteNamesOf(favorites), clock.instant())
    }
}
