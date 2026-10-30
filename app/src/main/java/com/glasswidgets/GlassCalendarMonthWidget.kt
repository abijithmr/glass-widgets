package com.glasswidgets

import android.content.Context
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Glass month calendar: a 6 x 7 grid with today highlighted. */
class GlassCalendarMonthWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews = create(context)

    companion object {
        private val LETTER_IDS = intArrayOf(
            R.id.mo_l_0, R.id.mo_l_1, R.id.mo_l_2, R.id.mo_l_3, R.id.mo_l_4, R.id.mo_l_5, R.id.mo_l_6
        )

        private val CELL_IDS = intArrayOf(
            R.id.mo_c_0, R.id.mo_c_1, R.id.mo_c_2, R.id.mo_c_3, R.id.mo_c_4, R.id.mo_c_5, R.id.mo_c_6,
            R.id.mo_c_7, R.id.mo_c_8, R.id.mo_c_9, R.id.mo_c_10, R.id.mo_c_11, R.id.mo_c_12, R.id.mo_c_13,
            R.id.mo_c_14, R.id.mo_c_15, R.id.mo_c_16, R.id.mo_c_17, R.id.mo_c_18, R.id.mo_c_19, R.id.mo_c_20,
            R.id.mo_c_21, R.id.mo_c_22, R.id.mo_c_23, R.id.mo_c_24, R.id.mo_c_25, R.id.mo_c_26, R.id.mo_c_27,
            R.id.mo_c_28, R.id.mo_c_29, R.id.mo_c_30, R.id.mo_c_31, R.id.mo_c_32, R.id.mo_c_33, R.id.mo_c_34,
            R.id.mo_c_35, R.id.mo_c_36, R.id.mo_c_37, R.id.mo_c_38, R.id.mo_c_39, R.id.mo_c_40, R.id.mo_c_41
        )

        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_calendar_month)
            Glass.applyCard(context, views, R.id.widget_root)

            val locale = Locale.getDefault()
            val today = Calendar.getInstance()
            views.setTextViewText(
                R.id.mo_title,
                SimpleDateFormat("MMMM yyyy", locale).format(today.time)
            )

            val first = today.firstDayOfWeek
            val letterDay = Calendar.getInstance()
            letterDay.set(Calendar.DAY_OF_WEEK, first)
            for (i in 0 until 7) {
                val letter = letterDay.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
                    ?.take(1)?.uppercase(locale) ?: ""
                views.setTextViewText(LETTER_IDS[i], letter)
                letterDay.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Grid starts on the first-day-of-week on or before the 1st of this month.
            val cursor = today.clone() as Calendar
            cursor.set(Calendar.DAY_OF_MONTH, 1)
            val offset = (cursor.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
            cursor.add(Calendar.DAY_OF_YEAR, -offset)

            val thisMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)

            for (cell in CELL_IDS) {
                val inMonth = cursor.get(Calendar.MONTH) == thisMonth
                val dayNumber = cursor.get(Calendar.DAY_OF_MONTH)
                views.setTextViewText(cell, dayNumber.toString())
                if (inMonth && dayNumber == todayDay) {
                    views.setInt(cell, "setBackgroundResource", R.drawable.glass_day_pill)
                    views.setTextColor(cell, Glass.TODAY_TEXT)
                } else {
                    views.setInt(cell, "setBackgroundResource", 0)
                    views.setTextColor(cell, if (inMonth) 0xE6FFFFFF.toInt() else Glass.DIM_TEXT)
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }

            views.setOnClickPendingIntent(R.id.widget_root, Glass.calendarPendingIntent(context))
            return views
        }
    }
}
