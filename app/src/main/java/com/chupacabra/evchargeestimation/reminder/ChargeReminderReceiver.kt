package com.chupacabra.evchargeestimation.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chupacabra.evchargeestimation.MainActivity
import com.chupacabra.evchargeestimation.util.ChargeReminders

/**
 * Fires when a charge timer/alarm is due and shows a system notification.
 */
class ChargeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val desired = intent?.getIntExtra(EXTRA_DESIRED_PERCENT, 80) ?: 80
        val kind = intent?.getStringExtra(EXTRA_KIND).orEmpty()
        val title = when (kind) {
            KIND_TIMER -> "Charge time is up"
            else -> "Charge reminder"
        }
        val body = "Your car should be around $desired% now."

        // No longer "active" in the UI once it has fired.
        ChargeReminders.markFired(context)

        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted — nothing more we can do here.
        }
    }

    companion object {
        const val CHANNEL_ID = "charge_reminders"
        const val NOTIFICATION_ID = 4201
        const val EXTRA_DESIRED_PERCENT = "desired_percent"
        const val EXTRA_KIND = "kind"
        const val KIND_TIMER = "timer"
        const val KIND_ALARM = "alarm"
        const val ACTION_FIRE = "com.chupacabra.evchargeestimation.CHARGE_REMINDER"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Charge reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when charging should be finished"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
