package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The home screen of the app: a live stage, look controls and the widget library. */
class MainActivity : AppCompatActivity() {

    private var adapter: WidgetAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val widgetAdapter = WidgetAdapter(
            all = WidgetCatalog.entries(),
            // Widgets already on the home screen pick up the new style or colour.
            onLookChanged = { Glass.refreshAll(this) },
            onAdd = { pin(it) }
        )
        adapter = widgetAdapter

        findViewById<RecyclerView>(R.id.widget_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            // Items are redrawn in place; the default cross-fade would flash the stage.
            itemAnimator = null
            adapter = widgetAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        // The count of widgets on the home screen may have changed while away.
        adapter?.refreshHeader()
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
