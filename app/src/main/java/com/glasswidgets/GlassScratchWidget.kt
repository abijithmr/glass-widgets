package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
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

        private val TEXT_IDS = intArrayOf(
            R.id.scratch_text_0, R.id.scratch_text_1, R.id.scratch_text_2, R.id.scratch_text_3,
            R.id.scratch_text_4, R.id.scratch_text_5, R.id.scratch_text_6
        )

        /** preview = true is for the in-app library: it shows sample text instead of a real note. */
        fun create(context: Context, widgetId: Int, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_scratch)
            val dark = ScratchStore.dark(context)
            val c = ScratchStore.palette(dark)

            views.setInt(R.id.scratch_root, "setBackgroundResource",
                if (dark) R.drawable.scratch_card_bg_dark else R.drawable.scratch_card_bg)
            views.setInt(R.id.scratch_header, "setBackgroundResource",
                if (dark) R.drawable.scratch_top_bg_dark else R.drawable.scratch_top_bg)
            views.setInt(R.id.scratch_footer, "setBackgroundResource",
                if (dark) R.drawable.scratch_bottom_bg_dark else R.drawable.scratch_bottom_bg)
            views.setInt(R.id.scratch_write, "setBackgroundResource",
                if (dark) R.drawable.scratch_btn_primary_dark else R.drawable.scratch_btn_primary)
            views.setTextColor(R.id.scratch_title, c.text)
            views.setTextColor(R.id.scratch_stats, c.sub)
            views.setTextColor(R.id.scratch_plain, c.sub)
            views.setTextColor(R.id.scratch_write, c.onAccent)

            // One hidden text view per font (a widget cannot change a font at run time), so only
            // the chosen one is shown and filled in.
            val font = ScratchStore.font(context)
            for (i in TEXT_IDS.indices) {
                views.setViewVisibility(TEXT_IDS[i], if (i == font) View.VISIBLE else View.GONE)
            }
            val textId = TEXT_IDS[font]

            val text = if (preview) PREVIEW_TEXT else ScratchStore.load(context, widgetId)
            if (text.isBlank()) {
                views.setTextViewText(textId, "Tap Write to start a note")
                views.setTextColor(textId, c.hint)
                views.setTextViewText(R.id.scratch_stats, "Empty")
            } else {
                views.setTextViewText(textId, text.take(SNIPPET_CHARS))
                views.setTextColor(textId, c.text)
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
