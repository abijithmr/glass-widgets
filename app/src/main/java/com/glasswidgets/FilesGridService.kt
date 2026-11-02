package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar

/**
 * Feeds the Files widget list. The current folder is split into date sections
 * (Today, Yesterday, ...); each section is a heading followed by rows of cells.
 */
class FilesGridService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return Factory(applicationContext, widgetId)
    }

    /** A heading, or one row of up to four entries. */
    private sealed class Row {
        data class Header(val text: String) : Row()
        data class Cells(val entries: List<FilesStore.Entry>) : Row()
    }

    private class Factory(
        private val context: Context,
        private val widgetId: Int
    ) : RemoteViewsFactory {

        private var rows: List<Row> = emptyList()

        override fun onCreate() {}

        // Runs off the main thread, so reading the folder here never blocks the home screen.
        override fun onDataSetChanged() {
            val started = SystemClock.elapsedRealtime()
            val path = FilesStore.currentPath(context, widgetId)
            val entries = if (FilesStore.hasAccess(context)) {
                try {
                    FilesStore.list(context, path)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            rows = buildRows(entries, GlassFilesWidget.columnsFor(context, widgetId))
            FilesStore.recordListing(
                context, path, entries.size, SystemClock.elapsedRealtime() - started
            )
        }

        private fun buildRows(entries: List<FilesStore.Entry>, columns: Int): List<Row> {
            if (entries.isEmpty()) return emptyList()
            val bounds = SectionBounds()
            val out = ArrayList<Row>()
            var section: String? = null
            var pending = ArrayList<FilesStore.Entry>()
            fun flush() {
                if (pending.isNotEmpty()) out.add(Row.Cells(pending))
                pending = ArrayList()
            }
            for (entry in entries) {
                val label = bounds.labelFor(entry.modified)
                if (label != section) {
                    flush()
                    section = label
                    out.add(Row.Header(label))
                }
                pending.add(entry)
                if (pending.size == columns) flush()
            }
            flush()
            return out
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews =
            when (val row = rows.getOrNull(position)) {
                is Row.Header -> {
                    val views = RemoteViews(context.packageName, R.layout.item_file_header)
                    views.setTextViewText(R.id.header_text, row.text)
                    views
                }
                is Row.Cells -> cellsView(row.entries)
                null -> RemoteViews(context.packageName, R.layout.item_file_header)
            }

        private fun cellsView(entries: List<FilesStore.Entry>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.item_file_row)
            val columns = GlassFilesWidget.columnsFor(context, widgetId)
            for (i in CELL.indices) {
                if (i >= columns) {
                    views.setViewVisibility(CELL[i], View.GONE)
                    continue
                }
                val entry = entries.getOrNull(i)
                if (entry == null) {
                    // Keep the empty slot so the last row lines up with the rows above it.
                    views.setViewVisibility(CELL[i], View.INVISIBLE)
                    continue
                }
                views.setViewVisibility(CELL[i], View.VISIBLE)
                views.setImageViewResource(
                    ICON[i],
                    if (entry.isDir) R.drawable.ic_folder_blue else R.drawable.ic_file_doc
                )
                views.setTextViewText(EXT[i], entry.ext)
                views.setViewVisibility(EXT[i], if (entry.isDir) View.GONE else View.VISIBLE)
                views.setTextViewText(NAME[i], entry.name)
                views.setTextViewText(SUB[i], entry.sub)
                val fillIn = Intent()
                    .putExtra(FilesStore.EXTRA_PATH, entry.path)
                    .putExtra(FilesStore.EXTRA_IS_DIR, entry.isDir)
                views.setOnClickFillInIntent(CELL[i], fillIn)
            }
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long {
            val row = rows.getOrNull(position) ?: return position.toLong()
            return when (row) {
                is Row.Header -> row.text.hashCode().toLong()
                is Row.Cells -> row.entries.first().path.hashCode().toLong()
            }
        }

        override fun hasStableIds(): Boolean = true

        companion object {
            private val CELL = intArrayOf(R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3)
            private val ICON = intArrayOf(
                R.id.cell_icon_0, R.id.cell_icon_1, R.id.cell_icon_2, R.id.cell_icon_3
            )
            private val EXT = intArrayOf(
                R.id.cell_ext_0, R.id.cell_ext_1, R.id.cell_ext_2, R.id.cell_ext_3
            )
            private val NAME = intArrayOf(
                R.id.cell_name_0, R.id.cell_name_1, R.id.cell_name_2, R.id.cell_name_3
            )
            private val SUB = intArrayOf(
                R.id.cell_sub_0, R.id.cell_sub_1, R.id.cell_sub_2, R.id.cell_sub_3
            )
        }
    }

    /** Day boundaries worked out once per listing. */
    private class SectionBounds {
        private val today: Long
        private val yesterday: Long
        private val week: Long
        private val month: Long

        init {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            today = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -1)
            yesterday = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -6)
            week = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -23)
            month = cal.timeInMillis
        }

        fun labelFor(modified: Long): String = when {
            modified >= today -> "Today"
            modified >= yesterday -> "Yesterday"
            modified >= week -> "Previous 7 Days"
            modified >= month -> "Previous 30 Days"
            else -> "Earlier"
        }
    }
}
