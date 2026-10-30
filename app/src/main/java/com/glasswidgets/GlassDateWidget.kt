package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** A square glass tile with the weekday, a big day number and the month. */
class GlassDateWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_date)
            Glass.applyCard(context, views, R.id.widget_root)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.calendarPendingIntent(context))
            return views
        }
    }
}
