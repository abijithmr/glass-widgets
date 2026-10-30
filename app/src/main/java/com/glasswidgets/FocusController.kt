package com.glasswidgets

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Calendar

/**
 * Turns Do Not Disturb on for a chosen time and back off again.
 * Android asks the person to allow "Do Not Disturb access" once; until then a tap opens that screen.
 */
object FocusController {

    val LABELS = listOf("For an hour", "For today", "Until this weekend", "Until I turn it off")

    /** Extra value meaning "flip DND on or off" (the moon button). */
    const val TOGGLE = -2

    private const val KEY_CHOICE = "focus_choice"
    private const val KEY_UNTIL = "focus_until"

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun hasAccess(context: Context): Boolean =
        manager(context)?.isNotificationPolicyAccessGranted ?: false

    /** The running choice (0..3), or -1 when Do Not Disturb is off or has ended. */
    fun activeChoice(context: Context): Int {
        val prefs = Glass.prefs(context)
        val choice = prefs.getInt(KEY_CHOICE, -1)
        if (choice < 0) return -1
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until != 0L && until <= System.currentTimeMillis()) return -1
        // The person may have switched it off from the quick settings.
        if (hasAccess(context) &&
            manager(context)?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        ) return -1
        return choice
    }

    /** 0 means "until I turn it off". */
    fun activeUntil(context: Context): Long = Glass.prefs(context).getLong(KEY_UNTIL, 0L)

    /** Called by the tap handler. Opens the access screen first if needed. */
    fun handle(context: Context, choice: Int) {
        if (!hasAccess(context)) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val running = activeChoice(context)
        when {
            choice == TOGGLE -> if (running >= 0) stop(context) else start(context, 3)
            choice == running -> stop(context)
            else -> start(context, choice)
        }
        Glass.refresh(context, GlassFocusWidget::class.java)
    }

    fun start(context: Context, choice: Int) {
        val until = untilFor(choice)
        manager(context)?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        Glass.prefs(context).edit()
            .putInt(KEY_CHOICE, choice)
            .putLong(KEY_UNTIL, until)
            .apply()
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (until != 0L) {
            // Inexact alarm: no special permission needed; a few minutes of drift is fine.
            alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, expiryIntent(context))
        } else {
            alarms?.cancel(expiryIntent(context))
        }
    }

    fun stop(context: Context) {
        if (hasAccess(context)) {
            manager(context)?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        Glass.prefs(context).edit().remove(KEY_CHOICE).remove(KEY_UNTIL).apply()
        context.getSystemService(AlarmManager::class.java)?.cancel(expiryIntent(context))
    }

    private fun untilFor(choice: Int): Long {
        val now = Calendar.getInstance()
        return when (choice) {
            0 -> now.timeInMillis + 60 * 60 * 1000L
            1 -> startOfDay(now, 1)
            2 -> {
                // Until Saturday begins; if it is already the weekend, until Monday.
                val dow = now.get(Calendar.DAY_OF_WEEK)
                val days = when (dow) {
                    Calendar.SUNDAY -> 1
                    Calendar.SATURDAY -> 2
                    else -> Calendar.SATURDAY - dow
                }
                startOfDay(now, days)
            }
            else -> 0L
        }
    }

    private fun startOfDay(from: Calendar, plusDays: Int): Long {
        val day = from.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, plusDays)
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        return day.timeInMillis
    }

    private fun expiryIntent(context: Context): PendingIntent {
        val intent = Intent(context, GlassFocusWidget::class.java)
            .setAction(GlassFocusWidget.ACTION_EXPIRE)
        return PendingIntent.getBroadcast(
            context,
            8,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
