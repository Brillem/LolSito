package com.example.lolsito

import android.app.Application
import com.example.lolsito.core.di.AppContainer
import com.example.lolsito.feature.reminders.work.ReminderNotifications

class LolSitoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Idempotent; the channel must exist before the first reminder, which may run without the UI.
        ReminderNotifications.createChannel(this)
    }
}
