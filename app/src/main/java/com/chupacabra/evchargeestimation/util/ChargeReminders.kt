package com.chupacabra.evchargeestimation.util

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import com.chupacabra.evchargeestimation.reminder.ChargeReminderReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reminders for when charging should finish.
 *
 * - Timer / Alarm: scheduled inside this app (AlarmManager + notification).
 * - Calendar: opens the phone's calendar app.
 *
 * Only one in-app reminder is kept at a time; setting a new one replaces the old.
 */
object ChargeReminders {

    data class ReadyAt(
        val endMillis: Long,
        val hourOfDay: Int,
        val minuteOfHour: Int,
        val durationMinutes: Int,
        val desiredPercent: Int
    )

    data class ActiveReminder(
        val kind: String,
        val endMillis: Long,
        val desiredPercent: Int
    ) {
        val kindLabel: String
            get() = if (kind == ChargeReminderReceiver.KIND_TIMER) "Timer" else "Alarm"

        val readyAtLabel: String
            get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(endMillis))
    }

    sealed class ScheduleResult {
        data class Scheduled(val readyAtLabel: String, val exact: Boolean) : ScheduleResult()
        data object NeedNotificationPermission : ScheduleResult()
        data object Failed : ScheduleResult()
    }

    fun readyAt(durationMinutes: Int, desiredPercent: Int): ReadyAt {
        val end = Calendar.getInstance().apply {
            add(Calendar.MINUTE, durationMinutes.coerceAtLeast(1))
        }
        return ReadyAt(
            endMillis = end.timeInMillis,
            hourOfDay = end.get(Calendar.HOUR_OF_DAY),
            minuteOfHour = end.get(Calendar.MINUTE),
            durationMinutes = durationMinutes.coerceAtLeast(1),
            desiredPercent = desiredPercent
        )
    }

    fun formatReadyClock(ready: ReadyAt): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ready.endMillis))

    fun message(desiredPercent: Int): String =
        "EV charged to $desiredPercent%"

    fun description(ready: ReadyAt): String {
        val wait = ChargeEstimator.formatDuration(ready.durationMinutes)
        return "Estimated $wait of charging to reach ${ready.desiredPercent}%."
    }

    fun needsNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Currently scheduled in-app timer/alarm, or null if none / already passed.
     */
    fun getActiveReminder(context: Context): ActiveReminder? {
        val prefs = prefs(context)
        val endMillis = prefs.getLong(KEY_END_MILLIS, 0L)
        if (endMillis <= System.currentTimeMillis()) {
            if (endMillis > 0L) clearStored(context)
            return null
        }
        val kind = prefs.getString(KEY_KIND, null) ?: return null
        val desired = prefs.getInt(KEY_DESIRED, 80)
        return ActiveReminder(kind = kind, endMillis = endMillis, desiredPercent = desired)
    }

    /**
     * Cancel any active timer/alarm and dismiss the charge notification if shown.
     * @return true if something was cleared
     */
    fun clearReminder(context: Context): Boolean {
        val appContext = context.applicationContext
        val hadStored = prefs(appContext).contains(KEY_END_MILLIS)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        // Cancel both request codes (timer and alarm share one active slot, but
        // either may have been the last scheduled kind).
        alarmManager?.let { am ->
            am.cancel(pendingIntent(appContext, ChargeReminderReceiver.KIND_TIMER))
            am.cancel(pendingIntent(appContext, ChargeReminderReceiver.KIND_ALARM))
        }

        try {
            NotificationManagerCompat.from(appContext)
                .cancel(ChargeReminderReceiver.NOTIFICATION_ID)
        } catch (_: Exception) {
        }

        clearStored(appContext)
        return hadStored
    }

    /**
     * Schedule an in-app timer/alarm for when the charge should be ready.
     * Replaces any previously scheduled in-app reminder.
     */
    fun scheduleInAppReminder(
        context: Context,
        ready: ReadyAt,
        kind: String
    ): ScheduleResult {
        if (needsNotificationPermission(context)) {
            return ScheduleResult.NeedNotificationPermission
        }

        ChargeReminderReceiver.ensureChannel(context)

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return ScheduleResult.Failed

        // One active reminder only — drop whatever was there before.
        clearReminder(appContext)

        val pending = pendingIntent(appContext, kind)
        val triggerAt = ready.endMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
        val exact = canUseExactAlarms(alarmManager)

        try {
            when {
                exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pending
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pending
                    )
                }
                else -> {
                    @Suppress("DEPRECATION")
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (_: Exception) {
                return ScheduleResult.Failed
            }
            saveActive(appContext, kind, triggerAt, ready.desiredPercent)
            return ScheduleResult.Scheduled(formatReadyClock(ready), exact = false)
        } catch (_: Exception) {
            return ScheduleResult.Failed
        }

        saveActive(appContext, kind, triggerAt, ready.desiredPercent)
        return ScheduleResult.Scheduled(formatReadyClock(ready), exact = exact)
    }

    fun scheduleTimer(context: Context, ready: ReadyAt): ScheduleResult =
        scheduleInAppReminder(context, ready, ChargeReminderReceiver.KIND_TIMER)

    fun scheduleAlarm(context: Context, ready: ReadyAt): ScheduleResult =
        scheduleInAppReminder(context, ready, ChargeReminderReceiver.KIND_ALARM)

    /** Called when the reminder fires so UI no longer shows it as active. */
    fun markFired(context: Context) {
        clearStored(context.applicationContext)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun canUseExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return canUseExactAlarms(am)
    }

    private fun canUseExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun pendingIntent(context: Context, kind: String): PendingIntent {
        val requestCode = when (kind) {
            ChargeReminderReceiver.KIND_TIMER -> REQUEST_TIMER
            else -> REQUEST_ALARM
        }
        // Extras must match what was used when scheduling for cancel() to work.
        val fireIntent = Intent(context, ChargeReminderReceiver::class.java).apply {
            action = ChargeReminderReceiver.ACTION_FIRE
            putExtra(ChargeReminderReceiver.EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun saveActive(
        context: Context,
        kind: String,
        endMillis: Long,
        desiredPercent: Int
    ) {
        prefs(context).edit()
            .putString(KEY_KIND, kind)
            .putLong(KEY_END_MILLIS, endMillis)
            .putInt(KEY_DESIRED, desiredPercent)
            .apply()
    }

    private fun clearStored(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // --- Calendar still uses the system calendar app ---

    fun calendarIntent(ready: ReadyAt): Intent {
        val start = System.currentTimeMillis()
        return Intent(Intent.ACTION_INSERT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(
                CalendarContract.Events.CONTENT_URI,
                "vnd.android.cursor.dir/event"
            )
            putExtra(CalendarContract.Events.TITLE, message(ready.desiredPercent))
            putExtra(CalendarContract.Events.DESCRIPTION, description(ready))
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ready.endMillis)
            putExtra(CalendarContract.Events.HAS_ALARM, 1)
        }
    }

    fun launchCalendar(context: Context, ready: ReadyAt): Boolean {
        if (tryStart(context, calendarIntent(ready))) return true
        val alt = Intent(Intent.ACTION_EDIT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            type = "vnd.android.cursor.item/event"
            putExtra(CalendarContract.Events.TITLE, message(ready.desiredPercent))
            putExtra(CalendarContract.Events.DESCRIPTION, description(ready))
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ready.endMillis)
        }
        return tryStart(context, alt)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private const val PREFS = "charge_reminders"
    private const val KEY_KIND = "kind"
    private const val KEY_END_MILLIS = "end_millis"
    private const val KEY_DESIRED = "desired_percent"
    private const val REQUEST_TIMER = 7101
    private const val REQUEST_ALARM = 7102
}
