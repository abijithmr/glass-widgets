package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.concurrent.thread

/**
 * Pick the city for the weather widget. Opens automatically when the widget is
 * added (it is the widget's configuration screen) and from the library.
 */
class WeatherSetupActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_setup)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the person backs out, the launcher must not add the widget.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        input = findViewById(R.id.city_input)
        status = findViewById(R.id.setup_status)
        results = findViewById(R.id.results)

        findViewById<MaterialButton>(R.id.btn_search).setOnClickListener { search() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }

        val saved = WeatherRepository.savedPlace(this)
        if (saved != null) {
            findViewById<MaterialButton>(R.id.btn_keep).apply {
                visibility = android.view.View.VISIBLE
                text = getString(R.string.setup_keep, saved.name)
                setOnClickListener { finishWithOk() }
            }
        }
    }

    private fun search() {
        val query = input.text.toString().trim()
        if (query.length < 2) {
            status.text = "Type at least two letters."
            return
        }
        status.text = "Searching…"
        results.removeAllViews()
        thread {
            val found = try {
                WeatherRepository.searchPlaces(query)
            } catch (e: Exception) {
                null
            }
            runOnUiThread { showResults(found) }
        }
    }

    private fun showResults(found: List<WeatherRepository.Place>?) {
        results.removeAllViews()
        when {
            found == null -> status.text = "Couldn't reach the weather service. Check your connection and try again."
            found.isEmpty() -> status.text = "No city found. Try another spelling."
            else -> {
                status.text = "Tap your city"
                found.forEach { results.addView(resultRow(it)) }
            }
        }
    }

    private fun resultRow(place: WeatherRepository.Place): TextView {
        val density = resources.displayMetrics.density
        val pad = (14 * density).toInt()
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        return TextView(this).apply {
            text = if (place.detail.isBlank()) place.name else "${place.name}\n${place.detail}"
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setPadding(pad, pad, pad, pad)
            setBackgroundResource(ripple.resourceId)
            isClickable = true
            setOnClickListener { choose(place) }
        }
    }

    private fun choose(place: WeatherRepository.Place) {
        WeatherRepository.savePlace(this, place)
        RefreshWorker.schedule(this)
        RefreshWorker.refreshNow(this)
        Glass.refresh(this, GlassWeatherWidget::class.java)
        Glass.refresh(this, GlassWeatherCardWidget::class.java)
        finishWithOk()
    }

    private fun finishWithOk() {
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
