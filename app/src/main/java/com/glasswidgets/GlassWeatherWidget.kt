package com.glasswidgets

import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews

/**
 * Glass weather card: date, temperature, feels-like, today's high and low,
 * and the Mon-Sun strip. Tap it to choose another city.
 */
class GlassWeatherWidget : DailyWidget() {

    override fun build(context: Context, appWidgetId: Int): RemoteViews = create(context)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancelIfUnused(context)
    }

    companion object {
        /** [preview] fills in sample numbers when no city has been chosen yet. */
        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_weather)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyWeekStrip(views)

            val place = WeatherRepository.savedPlace(context)
            val sample = WeatherRepository.Snapshot(28, 26, 29, 23, 1, 62, 11)
            val snapshot = WeatherRepository.cached(context) ?: if (preview) sample else null
            val cityName = place?.name ?: if (preview) "Kochi" else ""

            views.setTextViewText(R.id.weather_city, cityName)
            val unit = WeatherRepository.unitLabel(context)
            fun shown(celsius: Int?): Int? =
                celsius?.let { WeatherRepository.temperature(context, it) }

            views.setTextViewText(R.id.weather_temp, tempText(shown(snapshot?.temp), unit))
            views.setTextViewText(
                R.id.weather_icon,
                if (snapshot != null) WeatherRepository.symbolFor(snapshot.code) else "🌡️"
            )
            views.setTextViewText(
                R.id.weather_feels,
                when {
                    snapshot != null -> "Feels like ${shown(snapshot.feels)}°"
                    place == null -> "Tap to choose a city"
                    else -> "Updating…"
                }
            )
            views.setTextViewText(R.id.weather_high, "↑ ${shown(snapshot?.hi)?.toString() ?: "--"}°")
            views.setTextViewText(R.id.weather_low, "↓ ${shown(snapshot?.lo)?.toString() ?: "--"}°")

            val setup = Intent(context, WeatherSetupActivity::class.java)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.activity(context, setup, 3))
            return views
        }

        /** Big number with a smaller unit, e.g. 28°C. Also used by the weather card. */
        fun tempText(temp: Int?, unit: String): CharSequence {
            val text = SpannableStringBuilder(temp?.toString() ?: "--")
            val unitStart = text.length
            text.append(unit)
            text.setSpan(
                RelativeSizeSpan(0.42f),
                unitStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return text
        }
    }
}
