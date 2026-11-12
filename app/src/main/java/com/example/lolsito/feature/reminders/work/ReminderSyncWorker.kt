package com.example.lolsito.feature.reminders.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lolsito.LolSitoApplication
import kotlinx.coroutines.CancellationException

/** Runs [com.example.lolsito.feature.reminders.domain.SyncMatchReminders]; a failed sync is retried a few times. */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as LolSitoApplication).container.syncMatchReminders()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
