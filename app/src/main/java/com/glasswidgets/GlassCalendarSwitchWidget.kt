package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Glass calendar with a Week / Month switch (remembered for each widget),
 * plus "Add reminder" and "New event" buttons that open your calendar app.
 * It shows dates only; it does not read your events, so it needs no permission.
 */
class GlassCalendarSwitchWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews =
        create(context, appWidgetId)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SET_MODE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id == -1) return
            setMonthly(context, id, intent.getBooleanExtra(EXTRA_MONTHLY, false))
            AppWidgetManager.getInstance(context).updateAppWidget(id, build(context, id))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val edit = Glass.prefs(context).edit()
        for (id in appWidgetIds) edit.remove(key(id))
        edit.apply()
    }

    companion object {
        const val ACTION_SET_MODE = "com.glasswidgets.ACTION_CALENDAR_SET_MODE"
        private const val EXTRA_MONTHLY = "monthly"

        private val WEEK_LETTERS = intArrayOf(
            R.id.cs_wl_0, R.id.cs_wl_1, R.id.cs_wl_2, R.id.cs_wl_3, R.id.cs_wl_4, R.id.cs_wl_5, R.id.cs_wl_6
        )
        private val WEEK_DATES = intArrayOf(
            R.id.cs_wd_0, R.id.cs_wd_1, R.id.cs_wd_2, R.id.cs_wd_3, R.id.cs_wd_4, R.id.cs_wd_5, R.id.cs_wd_6
        )
        private val MONTH_LETTERS = intArrayOf(
            R.id.cs_ml_0, R.id.cs_ml_1, R.id.cs_ml_2, R.id.cs_ml_3, R.id.cs_ml_4, R.id.cs_ml_5, R.id.cs_ml_6
        )
        private val MONTH_CELLS = intArrayOf(
            R.id.cs_mc_0, R.id.cs_mc_1, R.id.cs_mc_2, R.id.cs_mc_3, R.id.cs_mc_4, R.id.cs_mc_5, R.id.cs_mc_6,
            R.id.cs_mc_7, R.id.cs_mc_8, R.id.cs_mc_9, R.id.cs_mc_10, R.id.cs_mc_11, R.id.cs_mc_12, R.id.cs_mc_13,
            R.id.cs_mc_14, R.id.cs_mc_15, R.id.cs_mc_16, R.id.cs_mc_17, R.id.cs_mc_18, R.id.cs_mc_19, R.id.cs_mc_20,
            R.id.cs_mc_21, R.id.cs_mc_22, R.id.cs_mc_23, R.id.cs_mc_24, R.id.cs_mc_25, R.id.cs_mc_26, R.id.cs_mc_27,
            R.id.cs_mc_28, R.id.cs_mc_29, R.id.cs_mc_30, R.id.cs_mc_31, R.id.cs_mc_32, R.id.cs_mc_33, R.id.cs_mc_34,
            R.id.cs_mc_35, R.id.cs_mc_36, R.id.cs_mc_37, R.id.cs_mc_38, R.id.cs_mc_39, R.id.cs_mc_40, R.id.cs_mc_41
        )

        private fun key(widgetId: Int) = "cs_monthly_$widgetId"

        private fun isMonthly(context: Context, widgetId: Int): Boolean =
            Glass.prefs(context).getBoolean(key(widgetId), false)

        private fun setMonthly(context: Context, widgetId: Int, monthly: Boolean) {
            Glass.prefs(context).edit().putBoolean(key(widgetId), monthly).apply()
        }

        /** [monthly] overrides the saved choice; used for the picture in the widget library. */
        fun create(context: Context, appWidgetId: Int, monthly: Boolean? = null): RemoteViews {
            val showMonth = monthly ?: isMonthly(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_glass_calendar_switch)
            Glass.applyCard(context, views, R.id.widget_root)

            val locale = Locale.getDefault()
            val today = Calendar.getInstance()
            views.setTextViewText(R.id.cs_month, SimpleDateFormat("MMMM", locale).format(today.time))
            views.setTextViewText(
                R.id.cs_date,
                if (showMonth) today.get(Calendar.YEAR).toString()
                else today.get(Calendar.DAY_OF_MONTH).toString()
            )
            views.setTextColor(R.id.cs_date, if (showMonth) 0x99FFFFFF.toInt() else 0xFFFFFFFF.toInt())

            // Week / Month switch: the active side gets the accent pill.
            for ((id, active) in listOf(R.id.cs_mode_week to !showMonth, R.id.cs_mode_month to showMonth)) {
                if (active) {
                    Glass.applyToday(context, views, id)
                } else {
                    views.setInt(id, "setBackgroundResource", 0)
                    views.setTextColor(id, 0xE6FFFFFF.toInt())
                }
            }
            views.setOnClickPendingIntent(R.id.cs_mode_week, modeIntent(context, appWidgetId, false))
            views.setOnClickPendingIntent(R.id.cs_mode_month, modeIntent(context, appWidgetId, true))

            views.setViewVisibility(R.id.cs_week_body, if (showMonth) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.cs_month_body, if (showMonth) View.VISIBLE else View.GONE)
            if (showMonth) fillMonth(context, views, today, locale) else fillWeek(context, views, today, locale)

            views.setOnClickPendingIntent(R.id.widget_root, Glass.calendarPendingIntent(context))

            // A reminder is a calendar entry with a notification; the calendar app opens ready to save it.
            val reminder = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "Reminder: ")
                .putExtra(CalendarContract.Events.HAS_ALARM, true)
            views.setOnClickPendingIntent(R.id.cs_btn_reminder, Glass.activity(context, reminder, 40))
            val event = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            views.setOnClickPendingIntent(R.id.cs_btn_event, Glass.activity(context, event, 41))
            return views
        }

        private fun fillWeek(context: Context, views: RemoteViews, now: Calendar, locale: Locale) {
            val first = now.firstDayOfWeek
            val todayIndex = (now.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
            val day = Calendar.getInstance()
            day.set(Calendar.DAY_OF_WEEK, first)
            for (i in 0 until 7) {
                val letter = day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
                    ?.take(1)?.uppercase(locale) ?: ""
                views.setTextViewText(WEEK_LETTERS[i], letter)
                views.setTextViewText(WEEK_DATES[i], day.get(Calendar.DAY_OF_MONTH).toString())
                if (i == todayIndex) {
                    Glass.applyToday(context, views, WEEK_DATES[i])
                } else {
                    views.setInt(WEEK_DATES[i], "setBackgroundResource", 0)
                    views.setTextColor(WEEK_DATES[i], 0xE6FFFFFF.toInt())
                }
                day.add(Calendar.DAY_OF_YEAR, 1)
            }
            views.setTextViewText(
                R.id.cs_today_line,
                SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(now.time)
            )
        }

        private fun fillMonth(context: Context, views: RemoteViews, today: Calendar, locale: Locale) {
            val first = today.firstDayOfWeek
            val letterDay = Calendar.getInstance()
            letterDay.set(Calendar.DAY_OF_WEEK, first)
            for (i in 0 until 7) {
                val letter = letterDay.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
                    ?.take(1)?.uppercase(locale) ?: ""
                views.setTextViewText(MONTH_LETTERS[i], letter)
                letterDay.add(Calendar.DAY_OF_YEAR, 1)
            }

            // The grid starts on the first day of the week on or before the 1st of this month.
            val cursor = today.clone() as Calendar
            cursor.set(Calendar.DAY_OF_MONTH, 1)
            val offset = (cursor.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
            cursor.add(Calendar.DAY_OF_YEAR, -offset)

            val thisMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            for (cell in MONTH_CELLS) {
                val inMonth = cursor.get(Calendar.MONTH) == thisMonth
                val dayNumber = cursor.get(Calendar.DAY_OF_MONTH)
                views.setTextViewText(cell, dayNumber.toString())
                if (inMonth && dayNumber == todayDay) {
                    Glass.applyToday(context, views, cell)
                } else {
                    views.setInt(cell, "setBackgroundResource", 0)
                    views.setTextColor(cell, if (inMonth) 0xE6FFFFFF.toInt() else Glass.DIM_TEXT)
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        private fun modeIntent(context: Context, widgetId: Int, monthly: Boolean): PendingIntent {
            val intent = Intent(context, GlassCalendarSwitchWidget::class.java)
                .setAction(ACTION_SET_MODE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_MONTHLY, monthly)
            return PendingIntent.getBroadcast(
                context,
                widgetId * 2 + if (monthly) 1 else 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
