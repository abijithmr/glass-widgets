package com.glasswidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Glass digital clock: big time, full date, and a Mon-Sun strip with today highlighted.
 * The time and date tick by themselves (TextClock). The strip only needs a refresh
 * when the day changes, so we refresh at midnight and when the date, time or timezone changes.
 */
class GlassDigitalClockWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
        scheduleMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_MIDNIGHT -> refreshAll(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(midnightIntent(context))
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, GlassDigitalClockWidget::class.java))
        if (ids.isNotEmpty()) {
            onUpdate(context, manager, ids)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glass_digital_clock)

        // Tap anywhere to open the device's clock / alarms app.
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)

        // Calendar.DAY_OF_WEEK: Sunday = 1 ... Saturday = 7. Our strip runs Monday (0) to Sunday (6).
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayIndex = (dayOfWeek + 5) % 7

        for ((index, viewId) in DAY_IDS.withIndex()) {
            if (index == todayIndex) {
                views.setInt(viewId, "setBackgroundResource", R.drawable.glass_day_pill)
                views.setTextColor(viewId, TODAY_TEXT)
            } else {
                views.setInt(viewId, "setBackgroundResource", 0)
                views.setTextColor(viewId, OTHER_TEXT)
            }
        }
        return views
    }

    private fun midnightIntent(context: Context): PendingIntent {
        val intent = Intent(context, GlassDigitalClockWidget::class.java).apply {
            action = ACTION_MIDNIGHT
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        // Inexact alarm: needs no special permission, and a few minutes of delay is fine here.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, next.timeInMillis, midnightIntent(context))
    }

    companion object {
        private const val ACTION_MIDNIGHT = "com.glasswidgets.ACTION_MIDNIGHT_REFRESH"

        private const val TODAY_TEXT = 0xFF14202A.toInt()
        private const val OTHER_TEXT = 0xB3FFFFFF.toInt()

        private val DAY_IDS = intArrayOf(
            R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6
        )
    }
}
