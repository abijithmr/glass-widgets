package com.glasswidgets

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** One widget shown in the in-app library. */
data class WidgetEntry(
    val title: String,
    val description: String,
    val sizeLabel: String,
    val provider: Class<out AppWidgetProvider>,
    /** null = fill the card width. */
    val previewWidthDp: Int?,
    val previewHeightDp: Int,
    val preview: (Context) -> RemoteViews,
    val secondaryLabel: String? = null,
    val onSecondary: ((Context) -> Unit)? = null
)

object WidgetCatalog {

    fun entries(): List<WidgetEntry> = listOf(
        WidgetEntry(
            title = "Digital Clock",
            description = "Time, the full date and a week strip with today highlighted.",
            sizeLabel = "4 × 2",
            provider = GlassDigitalClockWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 130,
            preview = { GlassDigitalClockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Analog Clock",
            description = "A clean transparent dial with white hands.",
            sizeLabel = "2 × 2",
            provider = GlassAnalogClockWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 130,
            preview = { GlassAnalogClockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Weather",
            description = "Temperature, feels-like, today's high and low. Pick your city once.",
            sizeLabel = "4 × 2",
            provider = GlassWeatherWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 140,
            preview = { GlassWeatherWidget.create(it, preview = true) },
            secondaryLabel = "Choose city",
            onSecondary = { it.startActivity(Intent(it, WeatherSetupActivity::class.java)) }
        ),
        WidgetEntry(
            title = "Week Calendar",
            description = "This week at a glance. Tap New event to add to your calendar.",
            sizeLabel = "4 × 3",
            provider = GlassCalendarWeekWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 180,
            preview = { GlassCalendarWeekWidget.create(it) }
        ),
        WidgetEntry(
            title = "Month Calendar",
            description = "A full month grid with today highlighted.",
            sizeLabel = "4 × 4",
            provider = GlassCalendarMonthWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 260,
            preview = { GlassCalendarMonthWidget.create(it) }
        ),
        WidgetEntry(
            title = "Control Tiles",
            description = "Round glass tiles that open Wi-Fi, Bluetooth, sound, display and camera.",
            sizeLabel = "2 × 3",
            provider = GlassControlsWidget::class.java,
            previewWidthDp = 146,
            previewHeightDp = 220,
            preview = { GlassControlsWidget.create(it) }
        ),
        WidgetEntry(
            title = "Battery",
            description = "Battery level, charging state and a progress bar.",
            sizeLabel = "2 × 2",
            provider = GlassBatteryWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 130,
            preview = { GlassBatteryWidget.create(it) }
        )
    )
}
