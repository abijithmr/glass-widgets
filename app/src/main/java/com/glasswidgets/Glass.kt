package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Shared helpers for every widget: the Frosted / Smoked style switch,
 * refreshing widgets, click intents and the Mon-Sun week strip.
 */
object Glass {

    const val STYLE_FROSTED = 0
    const val STYLE_SMOKED = 1
    const val STYLE_CLEAR = 2

    private const val PREFS = "glass_prefs"
    private const val KEY_STYLE = "style"
    private const val KEY_SCENE = "scene"
    private const val KEY_ACCENT = "accent"

    /** Text colour on the bright "today" pill. */
    val TODAY_TEXT: Int = 0xFF14202A.toInt()

    /** Text colour for ordinary labels on glass. */
    val OTHER_TEXT: Int = 0xB3FFFFFF.toInt()

    /** Text colour for days outside the current month. */
    val DIM_TEXT: Int = 0x4DFFFFFF

    val DAY_IDS: IntArray = intArrayOf(
        R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6
    )

    val providers: List<Class<out AppWidgetProvider>> = listOf(
        GlassDigitalClockWidget::class.java,
        GlassMiniClockWidget::class.java,
        GlassAnalogClockWidget::class.java,
        GlassDateWidget::class.java,
        GlassCountdownWidget::class.java,
        GlassWeatherWidget::class.java,
        GlassWeatherCardWidget::class.java,
        GlassCalendarWeekWidget::class.java,
        GlassCalendarMonthWidget::class.java,
        GlassFocusWidget::class.java,
        GlassControlsWidget::class.java,
        GlassDockWidget::class.java,
        GlassSearchWidget::class.java,
        GlassMusicWidget::class.java,
        GlassBatteryWidget::class.java,
        GlassMonitorWidget::class.java,
        GlassMusicCardWidget::class.java,
        GlassControlCenterWidget::class.java
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun style(context: Context): Int = prefs(context).getInt(KEY_STYLE, STYLE_FROSTED)

    fun setStyle(context: Context, style: Int) {
        prefs(context).edit().putInt(KEY_STYLE, style).apply()
    }

    fun sceneIndex(context: Context): Int =
        prefs(context).getInt(KEY_SCENE, 0).coerceIn(0, Look.scenes.lastIndex)

    fun scene(context: Context): Look.Scene = Look.scenes[sceneIndex(context)]

    fun setScene(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_SCENE, index).apply()
    }

    fun accentIndex(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, 0).coerceIn(0, Look.accents.lastIndex)

    fun accent(context: Context): Look.Accent = Look.accents[accentIndex(context)]

    fun setAccent(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, index).apply()
    }

    /** The highlight behind today's date, in the accent colour the user picked. */
    fun applyToday(context: Context, views: RemoteViews, viewId: Int) {
        views.setInt(viewId, "setBackgroundResource", accent(context).pillRes)
        views.setTextColor(viewId, TODAY_TEXT)
    }

    /** Glass background for a card-shaped widget. */
    fun applyCard(context: Context, views: RemoteViews, viewId: Int) {
        val res = when (style(context)) {
            STYLE_SMOKED -> R.drawable.glass_bg_smoked
            STYLE_CLEAR -> R.drawable.glass_bg_clear
            else -> R.drawable.glass_bg
        }
        views.setInt(viewId, "setBackgroundResource", res)
    }

    /** Glass background for a round or pill-shaped tile. */
    fun applyTile(context: Context, views: RemoteViews, viewId: Int) {
        val res = when (style(context)) {
            STYLE_SMOKED -> R.drawable.glass_tile_smoked
            STYLE_CLEAR -> R.drawable.glass_tile_clear
            else -> R.drawable.glass_tile
        }
        views.setInt(viewId, "setBackgroundResource", res)
    }

    /** Ask every instance of one widget to redraw. */
    fun refresh(context: Context, provider: Class<out AppWidgetProvider>) {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setClass(context, provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    fun refreshAll(context: Context) {
        providers.forEach { refresh(context, it) }
    }

    fun hasInstances(context: Context, provider: Class<out AppWidgetProvider>): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()

    fun activity(context: Context, intent: Intent, requestCode: Int): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Opens the device's clock / alarms app. */
    fun clockPendingIntent(context: Context): PendingIntent =
        activity(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), 1)

    /** Opens the device's calendar app. */
    fun calendarPendingIntent(context: Context): PendingIntent =
        activity(
            context,
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            2
        )

    /** 0 = Monday ... 6 = Sunday. */
    fun todayMondayIndex(): Int {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // Sunday = 1
        return (dayOfWeek + 5) % 7
    }

    /** Highlights today in a MON..SUN strip that uses ids day_0..day_6. */
    fun applyWeekStrip(context: Context, views: RemoteViews) {
        val today = todayMondayIndex()
        for ((index, viewId) in DAY_IDS.withIndex()) {
            if (index == today) {
                applyToday(context, views, viewId)
            } else {
                views.setInt(viewId, "setBackgroundResource", 0)
                views.setTextColor(viewId, OTHER_TEXT)
            }
        }
    }
}
