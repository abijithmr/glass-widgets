package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.widget.RemoteViews

/** Glass battery card: percentage, charging state and a progress bar. */
class GlassBatteryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context))
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancelIfUnused(context)
    }

    companion object {
        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_battery)
            Glass.applyCard(context, views, R.id.widget_root)

            val manager = context.getSystemService(BatteryManager::class.java)
            val level = (manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0)
                .coerceIn(0, 100)
            val charging = manager?.isCharging ?: false

            views.setTextViewText(R.id.battery_percent, "$level%")
            views.setTextViewText(
                R.id.battery_state,
                when {
                    charging -> "Charging"
                    level <= 20 -> "Low"
                    else -> "On battery"
                }
            )
            views.setProgressBar(R.id.battery_bar, 100, level, false)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                Glass.activity(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), 5)
            )
            return views
        }
    }
}
