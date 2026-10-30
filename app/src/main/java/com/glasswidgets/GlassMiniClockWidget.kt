package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** A slim glass pill with the time and the date. Both tick by themselves. */
class GlassMiniClockWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_mini_clock)
            Glass.applyTile(context, views, R.id.widget_root)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.clockPendingIntent(context))
            return views
        }
    }
}
