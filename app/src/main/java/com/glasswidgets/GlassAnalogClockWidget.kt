package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class GlassAnalogClockWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context))
        }
    }

    companion object {
        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_analog_clock)
            Glass.applyCard(context, views, R.id.widget_root)
            // Tap the clock to open the device's clock / alarms app.
            views.setOnClickPendingIntent(R.id.widget_root, Glass.clockPendingIntent(context))
            return views
        }
    }
}
