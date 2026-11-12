package com.example.lolsito.feature.reminders.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.lolsito.feature.reminders.domain.MatchReminder
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.reminders.domain.ScheduledReminder
import com.example.lolsito.feature.reminders.domain.reminderDelay
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * [ReminderScheduler] and [ReminderSyncTrigger] on WorkManager, which keeps the work across reboots.
 * Each reminder is a unique one-time work named after its match, so scheduling a match again replaces
 * it; its tags carry the match id and the [MatchReminder.version] (WorkInfo does not expose input data).
 * The sync runs as a 6-hour periodic work and as one-time requests, both only with a network.
 */
class WorkManagerReminders(context: Context, private val clock: Clock) : ReminderScheduler, ReminderSyncTrigger {
    private val appContext = context.applicationContext
    private val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    override suspend fun scheduled(): List<ScheduledReminder> =
        workManager.getWorkInfosByTagFlow(TAG_REMINDER).first()
            .filter { it.state != WorkInfo.State.CANCELLED && it.state != WorkInfo.State.FAILED }
            .mapNotNull { info ->
                val matchId = info.tags.tagValue(TAG_MATCH_PREFIX) ?: return@mapNotNull null
                ScheduledReminder(matchId, info.tags.tagValue(TAG_VERSION_PREFIX).orEmpty())
            }
            .distinctBy { it.matchId }

    override fun schedule(reminder: MatchReminder) {
        val now = clock.instant()
        // WorkManager prunes finished work at each process start once its retention has passed. The
        // record of a shown reminder must outlive the start of its match, or a sync in between would
        // not see it in scheduled() and would show the reminder again.
        val keepShownFor = Duration.between(now, reminder.startTime).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequest.Builder(MatchReminderWorker::class.java)
            .setInitialDelay(reminderDelay(reminder, now).toMillis(), TimeUnit.MILLISECONDS)
            .keepResultsForAtLeast(keepShownFor.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(MatchReminderWorker.inputOf(reminder))
            .addTag(TAG_REMINDER)
            .addTag(TAG_MATCH_PREFIX + reminder.matchId)
            .addTag(TAG_VERSION_PREFIX + reminder.version)
            .build()
        workManager.enqueueUniqueWork(reminderWorkName(reminder.matchId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(matchId: String) {
        workManager.cancelUniqueWork(reminderWorkName(matchId))
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_REMINDER)
    }

    /** A newer request replaces a pending or running one, so the sync always reads the latest choices. */
    override fun requestSync() {
        val request = OneTimeWorkRequest.Builder(ReminderSyncWorker::class.java)
            .setConstraints(networkRequired)
            .build()
        workManager.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Idempotent: an already scheduled periodic sync keeps its schedule. */
    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequest.Builder(ReminderSyncWorker::class.java, SYNC_PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(networkRequired)
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private val networkRequired: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun Set<String>.tagValue(prefix: String): String? =
        firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

    private companion object {
        const val TAG_REMINDER = "matchReminder"
        const val TAG_MATCH_PREFIX = "matchReminder.match:"
        const val TAG_VERSION_PREFIX = "matchReminder.version:"
        const val SYNC_NOW = "matchReminders.sync"
        const val SYNC_PERIODIC = "matchReminders.periodicSync"
        const val SYNC_PERIOD_HOURS = 6L

        fun reminderWorkName(matchId: String) = "matchReminder:$matchId"
    }
}
