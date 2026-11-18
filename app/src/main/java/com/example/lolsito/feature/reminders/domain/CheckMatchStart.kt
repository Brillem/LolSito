package com.example.lolsito.feature.reminders.domain

import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import kotlinx.coroutines.CancellationException
import java.time.Clock

/**
 * One check of whether a reminder's match has started. The bell, the favorites and the marked matches are
 * read again first, so a change made since the reminder was scheduled stops its checks. Then the live feed is asked: a
 * match in progress is to be notified; one that is not, or a feed that fails, gets another check from
 * the [scheduler] until the checks give up (see [startCheck]).
 *
 * [liveMatches] must come from the network only: a stored copy of the live feed would notify a match
 * that started long ago, or hide one that just did.
 */
class CheckMatchStart(
    private val preferences: UserPreferences,
    private val liveMatches: LiveMatchesRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    /** Returns whether the notification of [reminder] is to be shown now. */
    suspend operator fun invoke(reminder: MatchReminder): Boolean {
        if (!isStillWanted(reminder)) return false
        val liveMatchIds = try {
            matchIdsInProgress(liveMatches.getLiveMatches())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when (startCheck(reminder, liveMatchIds, clock.instant())) {
            StartCheck.NOTIFY -> true
            StartCheck.CHECK_AGAIN -> {
                scheduler.checkAgain(reminder)
                false
            }
            StartCheck.GIVE_UP -> false
        }
    }

    private suspend fun isStillWanted(reminder: MatchReminder): Boolean = try {
        reminder.isStillWanted(
            remindersEnabled = preferences.getMatchRemindersEnabled() ?: false,
            favoriteNames = favoriteNamesOf(preferences.getFavoriteTeams().orEmpty()),
            markedMatchIds = preferences.getMarkedMatches().orEmpty().map { it.matchId },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true // It was scheduled while wanted; an unreadable store is no reason to drop it.
    }
}
