package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** A glass search bar. Tap it to open your search app. */
class GlassSearchWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_search)
            Glass.applyTile(context, views, R.id.widget_root)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                Glass.activity(context, Intent(Intent.ACTION_WEB_SEARCH), 45)
            )
            return views
        }
    }
}
