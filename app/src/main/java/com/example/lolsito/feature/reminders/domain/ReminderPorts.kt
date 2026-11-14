package com.example.lolsito.feature.reminders.domain

/**
 * Port for the per-match reminders the system shows later. Each match has at most one reminder,
 * identified by its match id, so scheduling a match again replaces its reminder.
 */
interface ReminderScheduler {
    /**
     * The reminders scheduled and not cancelled, including those already shown (so a sync never
     * shows one twice), each with the [MatchReminder.version] it was scheduled with.
     */
    suspend fun scheduled(): List<ScheduledReminder>

    /** Schedules [reminder] for its [MatchReminder.triggerAt], replacing any reminder of the same match. */
    fun schedule(reminder: MatchReminder)

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
