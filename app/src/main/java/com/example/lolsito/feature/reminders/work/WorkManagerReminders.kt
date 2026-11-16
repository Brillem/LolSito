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
import com.example.lolsito.feature.reminders.domain.START_CHECK_GIVE_UP
import com.example.lolsito.feature.reminders.domain.START_CHECK_INTERVAL
import com.example.lolsito.feature.reminders.domain.ScheduledReminder
import com.example.lolsito.feature.reminders.domain.reminderDelay
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * [ReminderScheduler] and [ReminderSyncTrigger] on WorkManager, which keeps the work across reboots.
 * Each reminder is a unique work named after its match: a chain of one-time checks of whether the match
 * has started, each appended by the one before it, so scheduling a match again replaces the chain. Its
 * tags carry the match id and the [MatchReminder.version] (WorkInfo does not expose input data).
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
        val firstCheckIn = reminderDelay(reminder, clock.instant())
        workManager.enqueueUniqueWork(reminderWorkName(reminder.matchId), ExistingWorkPolicy.REPLACE, checkRequest(reminder, firstCheckIn))
    }

    /**
     * Appended to the reminder's own unique work, so it runs once the running check has finished and
     * [cancel] or a newer [schedule] drop the whole chain.
     */
    override fun checkAgain(reminder: MatchReminder) {
        workManager.enqueueUniqueWork(
            reminderWorkName(reminder.matchId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            checkRequest(reminder, START_CHECK_INTERVAL),
        )
    }

    /** One check of whether [reminder]'s match has started, [delay] from now and only with a network. */
    private fun checkRequest(reminder: MatchReminder, delay: Duration): OneTimeWorkRequest {
        // WorkManager prunes finished work at each process start once its retention has passed. The
        // record of a handled reminder must outlive the time its match stops qualifying for one, or a
        // sync in between would not see it in scheduled() and would schedule the reminder again.
        val keepHandledFor = Duration.between(clock.instant(), reminder.startTime.plus(START_CHECK_GIVE_UP))
            .coerceAtLeast(Duration.ZERO)
        return OneTimeWorkRequest.Builder(MatchReminderWorker::class.java)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(networkRequired)
            .keepResultsForAtLeast(keepHandledFor.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(MatchReminderWorker.inputOf(reminder))
            .addTag(TAG_REMINDER)
            .addTag(TAG_MATCH_PREFIX + reminder.matchId)
            .addTag(TAG_VERSION_PREFIX + reminder.version)
            .build()
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
