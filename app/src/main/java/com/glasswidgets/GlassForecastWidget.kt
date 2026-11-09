package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Glass forecast panel: current conditions, the next hours, the next 5 days,
 * and UV, humidity and wind. Weather data is from Open-Meteo.
 * "Radar" opens a weather map website in the browser; the map is not drawn here.
 */
class GlassForecastWidget : AppWidgetProvider() {

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
        if (intent.action == ACTION_TOGGLE_UNIT) {
            WeatherRepository.toggleUnit(context)
            Glass.refresh(context, GlassForecastWidget::class.java)
            Glass.refresh(context, GlassWeatherCardWidget::class.java)
            Glass.refresh(context, GlassWeatherWidget::class.java)
        }
    }

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
        const val ACTION_TOGGLE_UNIT = "com.glasswidgets.ACTION_FORECAST_TOGGLE_UNIT"

        private val HOUR_TIME = intArrayOf(
            R.id.fc_h_t_0, R.id.fc_h_t_1, R.id.fc_h_t_2, R.id.fc_h_t_3, R.id.fc_h_t_4, R.id.fc_h_t_5
        )
        private val HOUR_ICON = intArrayOf(
            R.id.fc_h_i_0, R.id.fc_h_i_1, R.id.fc_h_i_2, R.id.fc_h_i_3, R.id.fc_h_i_4, R.id.fc_h_i_5
        )
        private val HOUR_TEMP = intArrayOf(
            R.id.fc_h_v_0, R.id.fc_h_v_1, R.id.fc_h_v_2, R.id.fc_h_v_3, R.id.fc_h_v_4, R.id.fc_h_v_5
        )
        private val DAY_NAME = intArrayOf(
            R.id.fc_d_n_0, R.id.fc_d_n_1, R.id.fc_d_n_2, R.id.fc_d_n_3, R.id.fc_d_n_4
        )
        private val DAY_ICON = intArrayOf(
            R.id.fc_d_i_0, R.id.fc_d_i_1, R.id.fc_d_i_2, R.id.fc_d_i_3, R.id.fc_d_i_4
        )
        private val DAY_HI = intArrayOf(
            R.id.fc_d_h_0, R.id.fc_d_h_1, R.id.fc_d_h_2, R.id.fc_d_h_3, R.id.fc_d_h_4
        )
        private val DAY_LO = intArrayOf(
            R.id.fc_d_l_0, R.id.fc_d_l_1, R.id.fc_d_l_2, R.id.fc_d_l_3, R.id.fc_d_l_4
        )

        /** [preview] fills in sample numbers when nothing has been downloaded yet. */
        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_forecast)
            Glass.applyCard(context, views, R.id.widget_root)

            val place = WeatherRepository.savedPlace(context)
            val snapshot = WeatherRepository.cached(context)
                ?: if (preview) SAMPLE_NOW else null
            val forecast = WeatherRepository.forecast(context)
                ?: if (preview) SAMPLE_FORECAST else null
            val unit = WeatherRepository.unitLabel(context)

            fun shown(celsius: Int?): String =
                celsius?.let { WeatherRepository.temperature(context, it).toString() + "°" } ?: "--"

            views.setTextViewText(R.id.fc_city, place?.name ?: if (preview) "New York" else "Choose a city")
            views.setTextViewText(
                R.id.fc_unit,
                if (WeatherRepository.useFahrenheit(context)) "°F / °C" else "°C / °F"
            )
            views.setTextViewText(
                R.id.fc_temp,
                GlassWeatherWidget.tempText(
                    snapshot?.let { WeatherRepository.temperature(context, it.temp) },
                    unit
                )
            )
            views.setTextViewText(
                R.id.fc_cond,
                when {
                    snapshot != null -> WeatherRepository.describe(snapshot.code)
                    place == null -> "Tap to choose a city"
                    else -> "Updating…"
                }
            )
            views.setTextViewText(
                R.id.fc_hilo,
                snapshot?.let { "H ${shown(it.hi)}  L ${shown(it.lo)}" } ?: ""
            )
            views.setTextViewText(R.id.fc_hum, snapshot?.let { "${it.humidity}%" } ?: "--")
            views.setTextViewText(R.id.fc_wind, snapshot?.let { "${it.windKmh} km/h" } ?: "--")
            views.setTextViewText(
                R.id.fc_uv,
                forecast?.uv?.let { "$it  ${uvWord(it)}" } ?: "--"
            )

            val use24 = DateFormat.is24HourFormat(context)
            for (i in HOUR_TIME.indices) {
                val h = forecast?.hours?.getOrNull(i)
                views.setTextViewText(
                    HOUR_TIME[i],
                    when {
                        h == null -> ""
                        i == 0 -> "Now"
                        else -> hourLabel(h.hour, use24)
                    }
                )
                views.setTextViewText(HOUR_ICON[i], h?.let { WeatherRepository.symbolFor(it.code) } ?: "")
                views.setTextViewText(HOUR_TEMP[i], h?.let { shown(it.temp) } ?: "--")
            }

            val locale = Locale.getDefault()
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFormat = SimpleDateFormat("EEE", locale)
            for (i in DAY_NAME.indices) {
                val d = forecast?.days?.getOrNull(i)
                val name = when {
                    d == null -> ""
                    i == 0 -> "Today"
                    else -> parse.parse(d.date)?.let { dayFormat.format(it) } ?: ""
                }
                views.setTextViewText(DAY_NAME[i], name)
                views.setTextViewText(DAY_ICON[i], d?.let { WeatherRepository.symbolFor(it.code) } ?: "")
                views.setTextViewText(DAY_HI[i], d?.let { shown(it.hi) } ?: "--")
                views.setTextViewText(DAY_LO[i], d?.let { shown(it.lo) } ?: "")
            }

            val setup = Intent(context, WeatherSetupActivity::class.java)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.activity(context, setup, 30))

            val toggle = Intent(context, GlassForecastWidget::class.java).setAction(ACTION_TOGGLE_UNIT)
            views.setOnClickPendingIntent(
                R.id.fc_unit,
                PendingIntent.getBroadcast(
                    context, 31, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Radar opens a weather map in the browser, centred on the chosen city.
            val radarUrl = if (place != null) {
                "https://www.windy.com/?${place.lat},${place.lon},8"
            } else {
                "https://www.windy.com/"
            }
            val radar = Intent(Intent.ACTION_VIEW, Uri.parse(radarUrl))
            views.setOnClickPendingIntent(R.id.fc_radar, Glass.activity(context, radar, 32))
            return views
        }

        private fun hourLabel(hour: Int, use24: Boolean): String {
            if (use24) return "%02d".format(hour)
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            return h12.toString() + if (hour < 12) "am" else "pm"
        }

        /** The standard UV index bands. */
        private fun uvWord(uv: Int): String = when {
            uv <= 2 -> "Low"
            uv <= 5 -> "Moderate"
            uv <= 7 -> "High"
            uv <= 10 -> "Very high"
            else -> "Extreme"
        }

        // Only used for the picture in the app's widget library, never on the home screen.
        private val SAMPLE_NOW = WeatherRepository.Snapshot(22, 24, 25, 19, 2, 55, 15)
        private val SAMPLE_FORECAST = WeatherRepository.Forecast(
            hours = listOf(
                WeatherRepository.Hour(14, 22, 2), WeatherRepository.Hour(17, 21, 3),
                WeatherRepository.Hour(20, 19, 3), WeatherRepository.Hour(23, 18, 61),
                WeatherRepository.Hour(2, 17, 61), WeatherRepository.Hour(5, 17, 2)
            ),
            days = listOf(
                WeatherRepository.Day("2026-11-08", 25, 19, 2), WeatherRepository.Day("2026-11-09", 23, 17, 61),
                WeatherRepository.Day("2026-11-10", 21, 15, 3), WeatherRepository.Day("2026-11-11", 24, 16, 0),
                WeatherRepository.Day("2026-11-12", 26, 18, 1)
            ),
            uv = 4
        )
    }
}
