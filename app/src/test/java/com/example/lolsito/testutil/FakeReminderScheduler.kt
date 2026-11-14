package com.example.lolsito.testutil

import com.example.lolsito.feature.reminders.domain.MatchReminder
import com.example.lolsito.feature.reminders.domain.NotificationAvailability
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.reminders.domain.ScheduledReminder

/** In-memory [ReminderScheduler]: [reminders] is what is scheduled now; the lists record every call in order. */
class FakeReminderScheduler : ReminderScheduler {
    val reminders = linkedMapOf<String, MatchReminder>()
    val scheduledCalls = mutableListOf<MatchReminder>()
    val cancelledCalls = mutableListOf<String>()
    var cancelAllCalls = 0

    override suspend fun scheduled(): List<ScheduledReminder> =
        reminders.values.map { ScheduledReminder(it.matchId, it.version) }

    override fun schedule(reminder: MatchReminder) {
        scheduledCalls += reminder
        reminders[reminder.matchId] = reminder
    }

    override fun cancel(matchId: String) {
        cancelledCalls += matchId
        reminders.remove(matchId)
    }

    override fun cancelAll() {
        cancelAllCalls++
        reminders.clear()
    }
}

/** Counts the sync requests. */
class FakeReminderSyncTrigger : ReminderSyncTrigger {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

/** Answers [allowed], which a test flips to act as the system setting. */
class FakeNotificationAvailability(var allowed: Boolean = true) : NotificationAvailability {
    override fun canNotify(): Boolean = allowed
}
