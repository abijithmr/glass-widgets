package com.glasswidgets

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Shows every raw reading the System Monitor can use and lets the person choose which
 * sensor and which storage method the widget follows, so it can match other apps.
 */
class SensorsActivity : Activity() {

    private lateinit var report: TextView
    private lateinit var tempGroup: RadioGroup
    private var building = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.app_bg))
            addView(root)
        }
        setContentView(scroll)

        root.addView(text("Sensors & sources", 22f, true, 0xFFEAF2F5.toInt()))
        root.addView(text(
            "Pick which readings the System Monitor uses. If your game app or Settings shows a " +
                "different number, choose the sensor that matches it.", 13f, false, 0xFF8FA2AE.toInt()
        ).apply { setPadding(0, dp(6), 0, dp(14)) })

        root.addView(text("CPU temperature source", 15f, true, 0xFF4FD0E0.toInt()))
        tempGroup = RadioGroup(this)
        root.addView(tempGroup)

        root.addView(text("Storage used", 15f, true, 0xFF4FD0E0.toInt()).apply { setPadding(0, dp(18), 0, 0) })
        val storageGroup = RadioGroup(this)
        val storageOptions = listOf(
            SystemStats.STORAGE_SETTINGS to "Same as Android Settings (capacity − free)",
            SystemStats.STORAGE_PARTITION_FREE to "Capacity − space still available",
            SystemStats.STORAGE_DATA to "Data partition only"
        )
        for ((key, label) in storageOptions) {
            storageGroup.addView(radio(label, SystemStats.storageSource(this) == key) {
                SystemStats.setStorageSource(this, key)
                changed()
            })
        }
        root.addView(storageGroup)

        root.addView(text("Raw readings", 15f, true, 0xFF4FD0E0.toInt()).apply { setPadding(0, dp(18), 0, dp(6)) })
        report = text("", 11f, false, 0xFFD6E2E8.toInt()).apply { typeface = Typeface.MONOSPACE }
        root.addView(report)

        root.addView(Button(this).apply {
            this.text = "Copy readings"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("System Monitor readings", SystemStats.report(this@SensorsActivity)))
                Toast.makeText(this@SensorsActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        })
        buildTempOptions()
        report.text = SystemStats.report(this)
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun radio(label: String, checked: Boolean, onPick: () -> Unit) = RadioButton(this).apply {
        text = label
        setTextColor(0xFFEAF2F5.toInt())
        isChecked = checked
        textSize = 14f
        layoutParams = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnCheckedChangeListener { _, isOn -> if (isOn && !building) onPick() }
    }

    private fun buildTempOptions() {
        building = true
        tempGroup.removeAllViews()
        val current = SystemStats.tempSource(this)
        fun add(key: String, label: String) {
            tempGroup.addView(radio(label, current == key) {
                SystemStats.setTempSource(this, key)
                changed()
            })
        }
        add(SystemStats.SRC_AUTO, "Auto: hottest CPU sensor")
        add(SystemStats.SRC_AVG, "Average of CPU sensors")
        add(SystemStats.SRC_BATTERY, "Battery sensor")
        for (zone in SystemStats.zones().filter { it.celsius != null }.distinctBy { it.type }) {
            add(SystemStats.ZONE_PREFIX + zone.type, String.format(Locale.US, "%s · %.1f°C", zone.type, zone.celsius))
        }
        building = false
    }

    private fun changed() {
        Glass.refresh(applicationContext, GlassMonitorWidget::class.java)
        report.text = SystemStats.report(this)
    }

    override fun onResume() {
        super.onResume()
        // Temperatures move, so redraw the list and the report each time the screen opens
        buildTempOptions()
        report.text = SystemStats.report(this)
    }
}
