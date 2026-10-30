package com.glasswidgets

import android.content.Context
import android.widget.RemoteViews

/**
 * Glass digital clock: full date, big time and a Mon-Sun strip with today highlighted.
 * The time and date tick by themselves (TextClock); the strip is redrawn each day.
 */
class GlassDigitalClockWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews = create(context)

    companion object {
        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_digital_clock)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyWeekStrip(views)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.clockPendingIntent(context))
            return views
        }
    }
}
