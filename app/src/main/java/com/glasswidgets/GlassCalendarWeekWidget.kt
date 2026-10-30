package com.glasswidgets

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Glass week calendar: month, today's date and the current week.
 * Week starts on the day your region uses (Sunday or Monday).
 */
class GlassCalendarWeekWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews = create(context)

    companion object {
        private val LETTER_IDS = intArrayOf(
            R.id.wk_l_0, R.id.wk_l_1, R.id.wk_l_2, R.id.wk_l_3, R.id.wk_l_4, R.id.wk_l_5, R.id.wk_l_6
        )
        private val DATE_IDS = intArrayOf(
            R.id.wk_d_0, R.id.wk_d_1, R.id.wk_d_2, R.id.wk_d_3, R.id.wk_d_4, R.id.wk_d_5, R.id.wk_d_6
        )

        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_calendar_week)
            Glass.applyCard(context, views, R.id.widget_root)

            val locale = Locale.getDefault()
            val now = Calendar.getInstance()
            views.setTextViewText(R.id.cal_month, SimpleDateFormat("MMMM", locale).format(now.time))
            views.setTextViewText(R.id.cal_date, now.get(Calendar.DAY_OF_MONTH).toString())

            val first = now.firstDayOfWeek
            val todayIndex = (now.get(Calendar.DAY_OF_WEEK) - first + 7) % 7

            val day = Calendar.getInstance()
            day.set(Calendar.DAY_OF_WEEK, first)
            for (i in 0 until 7) {
                val letter = day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
                    ?.take(1)?.uppercase(locale) ?: ""
                views.setTextViewText(LETTER_IDS[i], letter)
                views.setTextViewText(DATE_IDS[i], day.get(Calendar.DAY_OF_MONTH).toString())
                if (i == todayIndex) {
                    views.setInt(DATE_IDS[i], "setBackgroundResource", R.drawable.glass_day_pill)
                    views.setTextColor(DATE_IDS[i], Glass.TODAY_TEXT)
                } else {
                    views.setInt(DATE_IDS[i], "setBackgroundResource", 0)
                    views.setTextColor(DATE_IDS[i], 0xE6FFFFFF.toInt())
                }
                day.add(Calendar.DAY_OF_YEAR, 1)
            }

            views.setOnClickPendingIntent(R.id.widget_root, Glass.calendarPendingIntent(context))
            views.setOnClickPendingIntent(R.id.btn_open_calendar, Glass.calendarPendingIntent(context))
            val newEvent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            views.setOnClickPendingIntent(R.id.btn_new_event, Glass.activity(context, newEvent, 4))
            return views
        }
    }
}
