package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * Name a countdown and pick its date. Opens when the countdown widget is added,
 * and again whenever the widget is tapped.
 */
class CountdownSetupActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown_setup)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.countdown_add_first, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // If the person backs out, the launcher must not add the widget.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        val titleInput = findViewById<TextInputEditText>(R.id.countdown_title_input)
        val picker = findViewById<DatePicker>(R.id.countdown_date_picker)

        val saved = Countdown.load(this, appWidgetId)
        val start = Calendar.getInstance()
        if (saved != null) {
            titleInput.setText(saved.title)
            start.timeInMillis = saved.dayMillis
        } else {
            start.add(Calendar.DAY_OF_YEAR, 7)
        }
        picker.updateDate(
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH)
        )

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            val day = Calendar.getInstance().apply {
                set(picker.year, picker.month, picker.dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val title = titleInput.text?.toString()?.trim().orEmpty().ifEmpty { "Countdown" }
            Countdown.save(this, appWidgetId, title, day.timeInMillis)
            AppWidgetManager.getInstance(this)
                .updateAppWidget(appWidgetId, GlassCountdownWidget.create(this, appWidgetId))
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
