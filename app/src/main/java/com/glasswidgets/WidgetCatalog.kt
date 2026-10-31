package com.glasswidgets

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

enum class Category(val label: String) {
    CLOCKS("Clocks"), WEATHER("Weather"), CALENDAR("Calendar"), CONTROLS("Controls")
}

/** One widget shown in the in-app library. */
data class WidgetEntry(
    val title: String,
    val category: Category,
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

    private val chooseCity: (Context) -> Unit =
        { it.startActivity(Intent(it, WeatherSetupActivity::class.java)) }

    fun entries(): List<WidgetEntry> = listOf(
        WidgetEntry(
            title = "Digital Clock",
            category = Category.CLOCKS,
            description = "Time, the full date and a week strip with today highlighted.",
            sizeLabel = "4 × 2",
            provider = GlassDigitalClockWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 140,
            preview = { GlassDigitalClockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Mini Clock",
            category = Category.CLOCKS,
            description = "A slim glass pill with the time and the date.",
            sizeLabel = "3 × 1",
            provider = GlassMiniClockWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 64,
            preview = { GlassMiniClockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Analog Clock",
            category = Category.CLOCKS,
            description = "A clean transparent dial with white hands.",
            sizeLabel = "2 × 2",
            provider = GlassAnalogClockWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 130,
            preview = { GlassAnalogClockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Date",
            category = Category.CALENDAR,
            description = "The weekday, a big day number and the month.",
            sizeLabel = "2 × 2",
            provider = GlassDateWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 140,
            preview = { GlassDateWidget.create(it) }
        ),
        WidgetEntry(
            title = "Countdown",
            category = Category.CALENDAR,
            description = "Days left until a date you choose. Tap the widget to change it.",
            sizeLabel = "2 × 2",
            provider = GlassCountdownWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 140,
            preview = { GlassCountdownWidget.create(it, 0, preview = true) }
        ),
        WidgetEntry(
            title = "Weather",
            category = Category.WEATHER,
            description = "Temperature, feels-like, today's high and low. Pick your city once.",
            sizeLabel = "4 × 2",
            provider = GlassWeatherWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 150,
            preview = { GlassWeatherWidget.create(it, preview = true) },
            secondaryLabel = "Choose city",
            onSecondary = chooseCity
        ),
        WidgetEntry(
            title = "Weather Card",
            category = Category.WEATHER,
            description = "Humidity, wind and feels-like, with a °C / °F switch on the card.",
            sizeLabel = "4 × 3",
            provider = GlassWeatherCardWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 190,
            preview = { GlassWeatherCardWidget.create(it, preview = true) },
            secondaryLabel = "Choose city",
            onSecondary = chooseCity
        ),
        WidgetEntry(
            title = "Week Calendar",
            category = Category.CALENDAR,
            description = "This week at a glance. Tap New event to add to your calendar.",
            sizeLabel = "4 × 3",
            provider = GlassCalendarWeekWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 180,
            preview = { GlassCalendarWeekWidget.create(it) }
        ),
        WidgetEntry(
            title = "Month Calendar",
            category = Category.CALENDAR,
            description = "A full month grid with today highlighted.",
            sizeLabel = "4 × 4",
            provider = GlassCalendarMonthWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 260,
            preview = { GlassCalendarMonthWidget.create(it) }
        ),
        WidgetEntry(
            title = "Focus",
            category = Category.CONTROLS,
            description = "Do Not Disturb for an hour, a day or until the weekend. " +
                "Android asks you to allow access the first time you tap it.",
            sizeLabel = "3 × 4",
            provider = GlassFocusWidget::class.java,
            previewWidthDp = 220,
            previewHeightDp = 300,
            preview = { GlassFocusWidget.create(it, preview = true) }
        ),
        WidgetEntry(
            title = "Control Tiles",
            category = Category.CONTROLS,
            description = "Round glass tiles that open Wi-Fi, Bluetooth, sound, display and camera.",
            sizeLabel = "2 × 3",
            provider = GlassControlsWidget::class.java,
            previewWidthDp = 146,
            previewHeightDp = 220,
            preview = { GlassControlsWidget.create(it) }
        ),
        WidgetEntry(
            title = "Dock",
            category = Category.CONTROLS,
            description = "A glass pill with shortcuts to Phone, Messages, Browser, Camera and Calendar.",
            sizeLabel = "4 × 1",
            provider = GlassDockWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 64,
            preview = { GlassDockWidget.create(it) }
        ),
        WidgetEntry(
            title = "Music Player",
            category = Category.CONTROLS,
            description = "Opens Apple Music (or your music app) with previous, play/pause and next buttons for whatever is playing.",
            sizeLabel = "4 × 2",
            provider = GlassMusicWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 130,
            preview = { GlassMusicWidget.create(it) }
        ),
        WidgetEntry(
            title = "Search",
            category = Category.CONTROLS,
            description = "A glass search bar that opens your search app.",
            sizeLabel = "4 × 1",
            provider = GlassSearchWidget::class.java,
            previewWidthDp = null,
            previewHeightDp = 64,
            preview = { GlassSearchWidget.create(it) }
        ),
        WidgetEntry(
            title = "Battery",
            category = Category.CONTROLS,
            description = "Battery level, charging state and a progress bar.",
            sizeLabel = "2 × 2",
            provider = GlassBatteryWidget::class.java,
            previewWidthDp = 130,
            previewHeightDp = 130,
            preview = { GlassBatteryWidget.create(it) }
        )
    )
}
