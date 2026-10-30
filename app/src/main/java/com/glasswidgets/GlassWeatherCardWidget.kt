package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Glass weather card: city, big temperature, condition, humidity, wind and feels-like.
 * The "°C / °F" chip switches units for every weather widget. Tap the card to change city.
 */
class GlassWeatherCardWidget : AppWidgetProvider() {

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
        const val ACTION_TOGGLE_UNIT = "com.glasswidgets.ACTION_TOGGLE_UNIT"

        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_weather_card)
            Glass.applyCard(context, views, R.id.widget_root)

            val place = WeatherRepository.savedPlace(context)
            val sample = WeatherRepository.Snapshot(22, 24, 25, 19, 2, 55, 15)
            val snapshot = WeatherRepository.cached(context) ?: if (preview) sample else null
            val city = place?.name ?: if (preview) "New York" else "Choose a city"
            val unit = WeatherRepository.unitLabel(context)

            fun shown(celsius: Int?): String =
                celsius?.let { WeatherRepository.temperature(context, it).toString() + "°" } ?: "--"

            views.setTextViewText(R.id.card_city, city)
            views.setTextViewText(
                R.id.card_unit,
                if (WeatherRepository.useFahrenheit(context)) "°F / °C" else "°C / °F"
            )
            views.setTextViewText(
                R.id.card_temp,
                GlassWeatherWidget.tempText(
                    snapshot?.let { WeatherRepository.temperature(context, it.temp) },
                    unit
                )
            )
            views.setTextViewText(
                R.id.card_condition,
                when {
                    snapshot != null -> WeatherRepository.describe(snapshot.code)
                    place == null -> "Tap to choose a city"
                    else -> "Updating…"
                }
            )
            views.setTextViewText(R.id.card_humidity, snapshot?.let { "${it.humidity}%" } ?: "--")
            views.setTextViewText(R.id.card_wind, snapshot?.let { "${it.windKmh} km/h" } ?: "--")
            views.setTextViewText(R.id.card_feels, shown(snapshot?.feels))

            val setup = Intent(context, WeatherSetupActivity::class.java)
            views.setOnClickPendingIntent(R.id.widget_root, Glass.activity(context, setup, 6))

            val toggle = Intent(context, GlassWeatherCardWidget::class.java)
                .setAction(ACTION_TOGGLE_UNIT)
            views.setOnClickPendingIntent(
                R.id.card_unit,
                PendingIntent.getBroadcast(
                    context,
                    7,
                    toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}
