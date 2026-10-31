package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The widget library: preview every widget, pick a glass style, add to the home screen. */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: WidgetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val entries = WidgetCatalog.entries()
        adapter = WidgetAdapter(
            entries = entries,
            currentStyle = { Glass.style(this) },
            onStyleChange = { style ->
                Glass.setStyle(this, style)
                // Redraw the previews (item 0 is the header, which keeps its selection)...
                adapter.notifyItemRangeChanged(1, entries.size)
                // ...and every widget already on the home screen.
                Glass.refreshAll(this)
            },
            onAdd = { pin(it) }
        )

        findViewById<RecyclerView>(R.id.widget_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
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
