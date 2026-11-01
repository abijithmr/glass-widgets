package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.widget.RemoteViews

/** Big glass battery bar with the exact charge. Refreshes with the shared 15-minute battery job. */
class GlassRechargeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = create(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_recharge)
            Glass.applyCard(context, views, R.id.widget_root)

            val manager = context.getSystemService(BatteryManager::class.java)
            val level = (manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0)
                .coerceIn(0, 100)
            val charging = manager?.isCharging ?: false

            views.setTextViewText(R.id.rc_percent, "$level%")
            views.setTextViewText(
                R.id.rc_state,
                when {
                    charging -> "Charging"
                    level <= 20 -> "Low battery"
                    else -> "On battery"
                }
            )
            views.setImageViewBitmap(R.id.rc_bar, RechargeBar.draw(level, charging))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                Glass.activity(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), 6)
            )
            return views
        }
    }
}
