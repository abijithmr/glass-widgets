package com.glasswidgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Feeds the Notes widget list. */
class NotesListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var notes: List<NotesStore.Note> = emptyList()

        override fun onCreate() {}

        // Runs off the main thread.
        override fun onDataSetChanged() {
            notes = NotesStore.all(context)
        }

        override fun onDestroy() {
            notes = emptyList()
        }

        override fun getCount(): Int = notes.size

        override fun getViewAt(position: Int): RemoteViews {
            val note = notes.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.item_note)
            return GlassNotesWidget.bindRow(context, note.text, note.done, note.id, true)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = notes.getOrNull(position)?.id ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
