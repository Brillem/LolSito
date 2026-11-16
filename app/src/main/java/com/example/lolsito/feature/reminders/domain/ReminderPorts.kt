package com.example.lolsito.feature.reminders.domain

/**
 * Port for the per-match reminders the system shows later. Each match has at most one reminder,
 * identified by its match id, so scheduling a match again replaces its reminder.
 */
interface ReminderScheduler {
    /**
     * The reminders scheduled and not cancelled, including those already shown or given up (so a sync
     * never shows one twice), each with the [MatchReminder.version] it was scheduled with.
     */
    suspend fun scheduled(): List<ScheduledReminder>

    /**
     * Schedules the first check of [reminder] for its [MatchReminder.triggerAt], replacing any reminder
     * of the same match.
     */
    fun schedule(reminder: MatchReminder)

    /**
     * Schedules another check of [reminder] after [START_CHECK_INTERVAL], to run once the current check
     * has finished. It belongs to the same reminder: [cancel] and a newer [schedule] drop it too.
     */
    fun checkAgain(reminder: MatchReminder)

    fun cancel(matchId: String)

    /** Cancels every reminder at once, without waiting for a sync or a network. */
    fun cancelAll()
}

/**
 * Whether the system lets the app show notifications right now: false when the notification permission
 * is not granted (Android 13+) or notifications are turned off for the app in the system settings.
 */
fun interface NotificationAvailability {
    fun canNotify(): Boolean
}

/** Asks for a reminder sync to run in the background as soon as there is a network. */
fun interface ReminderSyncTrigger {
    fun requestSync()
}
