package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup

/** The widget library: preview every widget, pick a glass style, add to the home screen. */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: WidgetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val entries = WidgetCatalog.entries()
        findViewById<TextView>(R.id.library_label).text =
            getString(R.string.library_title) + " · " + entries.size

        adapter = WidgetAdapter(entries) { pin(it) }
        findViewById<RecyclerView>(R.id.widget_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            adapter = this@MainActivity.adapter
        }

        val group = findViewById<MaterialButtonToggleGroup>(R.id.style_group)
        group.check(
            if (Glass.style(this) == Glass.STYLE_SMOKED) R.id.btn_smoked else R.id.btn_frosted
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val style = if (checkedId == R.id.btn_smoked) Glass.STYLE_SMOKED else Glass.STYLE_FROSTED
            Glass.setStyle(this, style)
            adapter.notifyDataSetChanged() // redraw previews
            Glass.refreshAll(this) // and every widget already on the home screen
        }
    }

    private fun pin(entry: WidgetEntry) {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, entry.provider), null, null)
        } else {
            Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
        }
    }
}
