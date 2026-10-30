package com.glasswidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Base class for widgets that show the date. It redraws the widget
 * at midnight and whenever the date, time or timezone changes.
 */
abstract class DailyWidget : AppWidgetProvider() {

    abstract fun build(context: Context, appWidgetId: Int): RemoteViews

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, build(context, id))
        }
        scheduleMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_MIDNIGHT -> Glass.refresh(context, javaClass)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    private fun midnightIntent(context: Context): PendingIntent {
        val intent = Intent(context, javaClass).setAction(ACTION_MIDNIGHT)
        return PendingIntent.getBroadcast(
            context,
            javaClass.name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        // Inexact alarm: no special permission needed, a few minutes of delay is fine.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, next.timeInMillis, midnightIntent(context))
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.glasswidgets.ACTION_MIDNIGHT_REFRESH"
    }
}
