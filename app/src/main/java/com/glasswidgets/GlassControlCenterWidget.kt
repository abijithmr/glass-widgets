package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews

/** Control centre: Focus pill, torch and airplane circles, and a music card. */
class GlassControlCenterWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = create(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    companion object {
        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_control_center)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyCard(context, views, R.id.cc_card)
            Glass.applyTile(context, views, R.id.cc_focus)
            Glass.applyTile(context, views, R.id.cc_art)

            // Focus (Do Not Disturb)
            val focusOn = !preview && FocusController.activeChoice(context) >= 0
            views.setTextViewText(
                R.id.cc_focus_sub,
                when {
                    preview -> "Off"
                    !FocusController.hasAccess(context) -> "Tap to allow"
                    focusOn -> "On"
                    else -> "Off"
                }
            )
            if (focusOn) {
                views.setInt(R.id.cc_moon, "setBackgroundResource", R.drawable.glass_circle_on)
                views.setInt(R.id.cc_moon_icon, "setColorFilter", Glass.TODAY_TEXT)
            } else {
                Glass.applyTile(context, views, R.id.cc_moon)
                views.setInt(R.id.cc_moon_icon, "setColorFilter", 0xFFFFFFFF.toInt())
            }
            views.setOnClickPendingIntent(
                R.id.cc_focus,
                Glass.activity(
                    context,
                    Intent(context, FocusActionActivity::class.java)
                        .putExtra(FocusActionActivity.EXTRA_CHOICE, FocusController.TOGGLE),
                    80
                )
            )

            // Torch
            val torchOn = !preview && TorchReceiver.isOn(context)
            if (torchOn) {
                views.setInt(R.id.cc_torch, "setBackgroundResource", R.drawable.glass_circle_on)
                views.setInt(R.id.cc_torch_icon, "setColorFilter", Glass.TODAY_TEXT)
            } else {
                Glass.applyTile(context, views, R.id.cc_torch)
                views.setInt(R.id.cc_torch_icon, "setColorFilter", 0xFFFFFFFF.toInt())
            }
            views.setOnClickPendingIntent(
                R.id.cc_torch,
                PendingIntent.getBroadcast(
                    context, 81, Intent(context, TorchReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Airplane mode (Android only lets the system flip it, so this opens its screen)
            Glass.applyTile(context, views, R.id.cc_airplane)
            views.setOnClickPendingIntent(
                R.id.cc_airplane,
                Glass.activity(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), 82)
            )

            // Music card
            NowPlaying.bind(
                context, views,
                NowPlaying.Ids(
                    box = R.id.cc_art_box, img = R.id.cc_art_img, placeholder = R.id.cc_art,
                    title = R.id.cc_title, sub = R.id.cc_sub, playIcon = R.id.cc_play_icon,
                    open = R.id.cc_open, prev = R.id.cc_prev, play = R.id.cc_play,
                    next = R.id.cc_next, bar = R.id.cc_bar
                ),
                requestBase = 83, preview = preview
            )
            return views
        }
    }
}
