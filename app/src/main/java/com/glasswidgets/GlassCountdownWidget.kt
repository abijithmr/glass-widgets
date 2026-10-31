package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One saved countdown: a name and the day it counts down to. */
class CountdownEvent(val title: String, val dayMillis: Long)

/** Saves one countdown per widget on the home screen. */
object Countdown {

    private fun titleKey(id: Int) = "countdown_title_$id"
    private fun dayKey(id: Int) = "countdown_day_$id"

    fun load(context: Context, id: Int): CountdownEvent? {
        val prefs = Glass.prefs(context)
        if (!prefs.contains(dayKey(id))) return null
        return CountdownEvent(
            prefs.getString(titleKey(id), null) ?: "Countdown",
            prefs.getLong(dayKey(id), 0L)
        )
    }

    fun save(context: Context, id: Int, title: String, dayMillis: Long) {
        Glass.prefs(context).edit()
            .putString(titleKey(id), title)
            .putLong(dayKey(id), dayMillis)
            .apply()
    }

    fun forget(context: Context, id: Int) {
        Glass.prefs(context).edit().remove(titleKey(id)).remove(dayKey(id)).apply()
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole calendar days from today to [dayMillis]; negative once it has passed. */
    fun daysUntil(dayMillis: Long): Int {
        val diff = startOfDay(dayMillis) - startOfDay(System.currentTimeMillis())
        // Rounding keeps the answer right across daylight-saving changes.
        return Math.round(diff / 86_400_000.0).toInt()
    }
}

/** Counts down the days to a date you choose. Each widget keeps its own date. */
class GlassCountdownWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews =
        create(context, appWidgetId)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { Countdown.forget(context, it) }
    }

    companion object {
        fun create(context: Context, appWidgetId: Int, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_countdown)
            Glass.applyCard(context, views, R.id.widget_root)
            views.setTextColor(R.id.countdown_days, Glass.accent(context).color)

            val event = if (preview) {
                val sample = System.currentTimeMillis() + 18L * 86_400_000L
                CountdownEvent("Birthday", sample)
            } else {
                Countdown.load(context, appWidgetId)
            }

            if (event == null) {
                views.setTextViewText(R.id.countdown_title, "Countdown")
                views.setTextViewText(R.id.countdown_days, "–")
                views.setTextViewText(R.id.countdown_unit, "Tap to pick a date")
                views.setTextViewText(R.id.countdown_date, "")
            } else {
                val days = Countdown.daysUntil(event.dayMillis)
                val count = Math.abs(days)
                views.setTextViewText(R.id.countdown_title, event.title)
                views.setTextViewText(R.id.countdown_days, count.toString())
                views.setTextViewText(
                    R.id.countdown_unit,
                    when {
                        days == 0 -> "It's today"
                        days > 0 -> if (count == 1) "day left" else "days left"
                        else -> if (count == 1) "day ago" else "days ago"
                    }
                )
                views.setTextViewText(
                    R.id.countdown_date,
                    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(event.dayMillis))
                )
            }

            // Tapping the widget changes its date.
            val edit = Intent(context, CountdownSetupActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                Glass.activity(context, edit, 1000 + appWidgetId)
            )
            return views
        }
    }
}
