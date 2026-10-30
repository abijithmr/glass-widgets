package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glass "Do Not Disturb" panel: four shortcut circles, the current mode and a list of durations.
 * Tap a duration to start Do Not Disturb; tap it again (or the moon) to stop.
 */
class GlassFocusWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_EXPIRE) {
            FocusController.stop(context)
            Glass.refresh(context, GlassFocusWidget::class.java)
        }
    }

    companion object {
        const val ACTION_EXPIRE = "com.glasswidgets.ACTION_FOCUS_EXPIRE"

        private val ROW_IDS = intArrayOf(
            R.id.focus_row_0, R.id.focus_row_1, R.id.focus_row_2, R.id.focus_row_3
        )

        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_focus)
            Glass.applyCard(context, views, R.id.widget_root)

            // The library preview shows the panel as if "For today" were running.
            val choice = if (preview) 1 else FocusController.activeChoice(context)
            val active = choice >= 0

            views.setTextViewText(R.id.focus_title, "Do Not Disturb")
            views.setTextViewText(
                R.id.focus_sub,
                when {
                    preview -> "On until midnight"
                    !FocusController.hasAccess(context) -> "Tap to allow access"
                    !active -> "Off"
                    FocusController.activeUntil(context) == 0L -> "On until you turn it off"
                    else -> "On until " + SimpleDateFormat("EEE h:mm a", Locale.getDefault())
                        .format(Date(FocusController.activeUntil(context)))
                }
            )

            // Shortcut circles
            listOf(R.id.focus_alarm, R.id.focus_bell, R.id.focus_airplane).forEach {
                Glass.applyTile(context, views, it)
            }
            if (active) {
                views.setInt(R.id.focus_moon, "setBackgroundResource", R.drawable.glass_circle_on)
                views.setInt(R.id.focus_moon_icon, "setColorFilter", Glass.TODAY_TEXT)
            } else {
                Glass.applyTile(context, views, R.id.focus_moon)
                views.setInt(R.id.focus_moon_icon, "setColorFilter", 0xFFFFFFFF.toInt())
            }

            views.setOnClickPendingIntent(R.id.focus_alarm, Glass.clockPendingIntent(context))
            views.setOnClickPendingIntent(
                R.id.focus_bell, Glass.activity(context, Intent(Settings.ACTION_SOUND_SETTINGS), 13)
            )
            views.setOnClickPendingIntent(
                R.id.focus_airplane,
                Glass.activity(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), 21)
            )
            views.setOnClickPendingIntent(R.id.focus_moon, choicePendingIntent(context, FocusController.TOGGLE, 22))

            // Duration list
            for ((index, rowId) in ROW_IDS.withIndex()) {
                val selected = index == choice
                views.setTextViewText(
                    rowId,
                    (if (selected) "✓  " else "") + FocusController.LABELS[index]
                )
                views.setInt(
                    rowId,
                    "setBackgroundResource",
                    if (selected) R.drawable.glass_row_selected else 0
                )
                views.setOnClickPendingIntent(rowId, choicePendingIntent(context, index, 30 + index))
            }
            return views
        }

        private fun choicePendingIntent(context: Context, choice: Int, requestCode: Int) =
            Glass.activity(
                context,
                Intent(context, FocusActionActivity::class.java)
                    .putExtra(FocusActionActivity.EXTRA_CHOICE, choice),
                requestCode
            )
    }
}
