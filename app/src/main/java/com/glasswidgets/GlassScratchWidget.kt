package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Scratchpad: a plain white text pad. The widget shows the start of the note; "Write" (or a tap
 * anywhere) opens the editor. Nothing runs in the background.
 */
class GlassScratchWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, create(context, id))
    }

    companion object {
        private const val PREVIEW_TEXT =
            "Groceries for the weekend\n- rice, dal, tomatoes\n- milk (2 packs)\n- coriander\n\n" +
                "Call Ravi about the trip"
        private const val SNIPPET_CHARS = 1500

        /** Redraw every Scratchpad widget. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            for (id in manager.getAppWidgetIds(ComponentName(app, GlassScratchWidget::class.java))) {
                manager.updateAppWidget(id, create(app, id))
            }
        }

        /** preview = true is for the in-app library: it shows sample text instead of a real note. */
        fun create(context: Context, widgetId: Int, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_scratch)
            val text = if (preview) PREVIEW_TEXT else ScratchStore.load(context, widgetId)
            if (text.isBlank()) {
                views.setTextViewText(R.id.scratch_text, "Tap Write to start a note")
                views.setTextColor(R.id.scratch_text, 0xFF9AA3B5.toInt())
                views.setTextViewText(R.id.scratch_stats, "Empty")
            } else {
                views.setTextViewText(R.id.scratch_text, text.take(SNIPPET_CHARS))
                views.setTextColor(R.id.scratch_text, 0xFF1D2330.toInt())
                val lines = ScratchStore.lines(text)
                views.setTextViewText(
                    R.id.scratch_stats,
                    "${if (lines == 1) "1 line" else "$lines lines"} · ${ScratchStore.words(text)} words"
                )
            }
            if (!preview) {
                val open = Glass.activity(
                    context,
                    Intent(context, ScratchEditActivity::class.java)
                        .putExtra(ScratchStore.EXTRA_WIDGET_ID, widgetId),
                    5000 + widgetId
                )
                views.setOnClickPendingIntent(R.id.scratch_root, open)
                views.setOnClickPendingIntent(R.id.scratch_write, open)
            }
            return views
        }
    }
}
