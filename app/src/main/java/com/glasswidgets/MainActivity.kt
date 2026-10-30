package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E1014"))
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        val body = TextView(this).apply {
            text = getString(R.string.main_hint)
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }

        val analogButton = Button(this).apply {
            text = getString(R.string.add_analog_widget)
            setOnClickListener { requestPinWidget(GlassAnalogClockWidget::class.java) }
        }

        val digitalButton = Button(this).apply {
            text = getString(R.string.add_digital_widget)
            setOnClickListener { requestPinWidget(GlassDigitalClockWidget::class.java) }
        }

        root.addView(title)
        root.addView(body)
        root.addView(analogButton)
        root.addView(digitalButton)
        setContentView(root)
    }

    private fun requestPinWidget(provider: Class<*>) {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(
                ComponentName(this, provider),
                null,
                null
            )
        } else {
            Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
        }
    }
}
