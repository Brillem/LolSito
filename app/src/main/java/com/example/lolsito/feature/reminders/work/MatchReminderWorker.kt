package com.example.lolsito.feature.reminders.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.lolsito.LolSitoApplication
import com.example.lolsito.feature.reminders.domain.MatchReminder
import com.example.lolsito.feature.reminders.domain.isStillWanted
import com.example.lolsito.feature.reminders.domain.reminderTitle
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * Shows one match's reminder when it is due. The bell and the favorites are checked again first, so
 * a change made without a network (the sync needs one) still stops the reminder. It always succeeds,
 * so the scheduler counts the reminder as handled and a later sync does not show it again.
 */
class MatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminder = reminderOf(inputData) ?: return Result.success()
        val container = (applicationContext as LolSitoApplication).container
        val preferences = container.userPreferences
        val wanted = try {
            reminder.isStillWanted(
                remindersEnabled = preferences.getMatchRemindersEnabled() ?: false,
                favoriteNames = favoriteNamesOf(preferences.getFavoriteTeams().orEmpty()),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true // It was scheduled while wanted; an unreadable store is no reason to drop it.
        }
        if (wanted) {
            val title = reminderTitle(reminder.teamsLabel, reminder.startTime, container.clock().instant())
            ReminderNotifications.show(applicationContext, reminder, title)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_MATCH_ID = "matchId"
        private const val KEY_START = "startEpochMillis"
        private const val KEY_TEAMS_LABEL = "teamsLabel"
        private const val KEY_DETAIL = "detail"
        private const val KEY_TEAM_NAMES = "teamNames"

        fun inputOf(reminder: MatchReminder): Data = Data.Builder()
            .putString(KEY_MATCH_ID, reminder.matchId)
            .putLong(KEY_START, reminder.startTime.toEpochMilli())
            .putString(KEY_TEAMS_LABEL, reminder.teamsLabel)
            .putString(KEY_DETAIL, reminder.detail)
            .putStringArray(KEY_TEAM_NAMES, reminder.teamNames.toTypedArray())
            .build()

        private fun reminderOf(data: Data): MatchReminder? {
            val matchId = data.getString(KEY_MATCH_ID) ?: return null
            val start = data.getLong(KEY_START, -1L).takeIf { it >= 0 } ?: return null
            return MatchReminder(
                matchId = matchId,
                startTime = Instant.ofEpochMilli(start),
                teamsLabel = data.getString(KEY_TEAMS_LABEL).orEmpty(),
                detail = data.getString(KEY_DETAIL).orEmpty(),
                teamNames = data.getStringArray(KEY_TEAM_NAMES)?.toList().orEmpty(),
            )
        }
    }
}
