package com.example.lolsito.feature.reminders.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.lolsito.MainActivity
import com.example.lolsito.R
import com.example.lolsito.feature.reminders.domain.MatchReminder

/** The match reminders channel and notifications; tapping one opens Match detail of its match. */
object ReminderNotifications {
    private const val CHANNEL_ID = "match_reminders"

    /** One notification per match: the tag is the match id, so a match never shows two reminders. */
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminders_channel_name))
            .setDescription(context.getString(R.string.reminders_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: MatchReminder, title: String) {
        if (!canNotify(context)) return
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.matchId.hashCode(),
            MainActivity.openMatchIntent(context, reminder.matchId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_match_reminder)
            .setContentTitle(title)
            .setContentText(reminder.detail)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(reminder.startTime.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.matchId, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call: nothing to show.
        }
    }

    private fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
