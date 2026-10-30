package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.widget.RemoteViews

/**
 * Glass control tiles in the style of the iPad control centre.
 * Android doesn't let a widget flip system switches, so each tile opens
 * the matching settings screen (or the camera) in one tap.
 */
class GlassControlsWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_controls)

            val tiles = intArrayOf(
                R.id.tile_wifi, R.id.tile_bluetooth, R.id.tile_sound, R.id.tile_display, R.id.tile_camera
            )
            tiles.forEach { Glass.applyTile(context, views, it) }

            views.setOnClickPendingIntent(
                R.id.tile_wifi, Glass.activity(context, Intent(Settings.ACTION_WIFI_SETTINGS), 11)
            )
            views.setOnClickPendingIntent(
                R.id.tile_bluetooth, Glass.activity(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 12)
            )
            views.setOnClickPendingIntent(
                R.id.tile_sound, Glass.activity(context, Intent(Settings.ACTION_SOUND_SETTINGS), 13)
            )
            views.setOnClickPendingIntent(
                R.id.tile_display, Glass.activity(context, Intent(Settings.ACTION_DISPLAY_SETTINGS), 14)
            )
            views.setOnClickPendingIntent(
                R.id.tile_camera,
                Glass.activity(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 15)
            )
            return views
        }
    }
}
