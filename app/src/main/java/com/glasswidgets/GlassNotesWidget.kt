package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews

/**
 * Notes: a glass checklist. Tap a circle to tick a note, tap its text to rename or delete it,
 * "Add note" opens a small typing pop-up. Nothing runs in the background.
 */
class GlassNotesWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context, id))
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.notes_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_DONE -> {
                NotesStore.clearDone(context)
                refreshAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_CLEAR_DONE = "com.glasswidgets.notes.CLEAR_DONE"

        /** Redraw every Notes widget: the header now, and the list once. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, GlassNotesWidget::class.java))
            for (id in ids) {
                manager.partiallyUpdateAppWidget(id, create(app, id, withAdapter = false))
                manager.notifyAppWidgetViewDataChanged(id, R.id.notes_list)
            }
        }

        private const val DONE_TEXT = 0x73FFFFFF

        /** One row of the list, shared by the live list and the library preview. */
        fun bindRow(
            context: Context, text: String, done: Boolean, id: Long, withTaps: Boolean
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.item_note)
            row.setImageViewResource(
                R.id.note_tick,
                if (done) R.drawable.note_tick_done else R.drawable.note_tick_open
            )
            if (done) {
                val styled = SpannableString(text)
                styled.setSpan(StrikethroughSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(DONE_TEXT), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                row.setTextViewText(R.id.note_text, styled)
            } else {
                row.setTextViewText(R.id.note_text, text)
            }
            if (withTaps) {
                row.setOnClickFillInIntent(
                    R.id.note_tick_box,
                    Intent().putExtra(NotesStore.EXTRA_ID, id).putExtra(NotesStore.EXTRA_KIND, NotesStore.KIND_TOGGLE)
                )
                row.setOnClickFillInIntent(
                    R.id.note_text,
                    Intent().putExtra(NotesStore.EXTRA_ID, id).putExtra(NotesStore.EXTRA_KIND, NotesStore.KIND_EDIT)
                )
            }
            return row
        }

        /**
         * withAdapter = false only redraws the header, so the list reloads exactly once.
         * preview = true is for the in-app library, which cannot host a live list: it shows sample rows.
         */
        fun create(
            context: Context,
            widgetId: Int,
            withAdapter: Boolean = true,
            preview: Boolean = false
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_notes)
            Glass.applyCard(context, views, R.id.widget_root)

            val open = if (preview) 2 else NotesStore.openCount(context)
            val total = if (preview) 3 else NotesStore.total(context)
            val done = if (preview) 1 else NotesStore.doneCount(context)
            views.setTextViewText(
                R.id.notes_count,
                when {
                    total == 0 -> "Nothing yet"
                    open == 0 -> "All done"
                    else -> "$open left"
                }
            )
            views.setViewVisibility(R.id.notes_clear, if (done > 0) View.VISIBLE else View.INVISIBLE)

            if (preview) {
                views.setViewVisibility(R.id.notes_list, View.GONE)
                views.setViewVisibility(R.id.notes_sample, View.VISIBLE)
                views.removeAllViews(R.id.notes_sample)
                views.addView(R.id.notes_sample, bindRow(context, "Buy milk and eggs", false, 0, false))
                views.addView(R.id.notes_sample, bindRow(context, "Call the electrician", false, 0, false))
                views.addView(R.id.notes_sample, bindRow(context, "Print boarding pass", true, 0, false))
                return views
            }

            views.setOnClickPendingIntent(
                R.id.notes_clear,
                PendingIntent.getBroadcast(
                    context, 4100,
                    Intent(context, GlassNotesWidget::class.java).setAction(ACTION_CLEAR_DONE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.notes_add,
                Glass.activity(context, Intent(context, NotesAddActivity::class.java), 4101)
            )

            if (withAdapter) {
                val service = Intent(context, NotesListService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // A unique data URI keeps each widget's list separate.
                service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
                views.setRemoteAdapter(R.id.notes_list, service)
                views.setEmptyView(R.id.notes_list, R.id.notes_empty)
                views.setPendingIntentTemplate(
                    R.id.notes_list,
                    PendingIntent.getActivity(
                        context,
                        4200 + widgetId,
                        Intent(context, NotesActionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            }
            return views
        }
    }
}
