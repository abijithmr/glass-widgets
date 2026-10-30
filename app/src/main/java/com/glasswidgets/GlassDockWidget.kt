package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.RemoteViews

/** A glass pill with shortcuts to Phone, Messages, Browser, Camera and Calendar. */
class GlassDockWidget : AppWidgetProvider() {

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
        private fun appIntent(category: String): Intent =
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_dock)
            Glass.applyTile(context, views, R.id.widget_root)

            views.setOnClickPendingIntent(
                R.id.dock_phone, Glass.activity(context, Intent(Intent.ACTION_DIAL), 41)
            )
            views.setOnClickPendingIntent(
                R.id.dock_message,
                Glass.activity(context, appIntent(Intent.CATEGORY_APP_MESSAGING), 42)
            )
            views.setOnClickPendingIntent(
                R.id.dock_browser,
                Glass.activity(context, appIntent(Intent.CATEGORY_APP_BROWSER), 43)
            )
            views.setOnClickPendingIntent(
                R.id.dock_camera,
                Glass.activity(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 44)
            )
            views.setOnClickPendingIntent(R.id.dock_calendar, Glass.calendarPendingIntent(context))
            return views
        }
    }
}
