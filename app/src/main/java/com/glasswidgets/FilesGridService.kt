package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Feeds the Files widget grid with the contents of the current folder. */
class FilesGridService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return Factory(applicationContext, widgetId)
    }

    private class Factory(
        private val context: Context,
        private val widgetId: Int
    ) : RemoteViewsFactory {

        private var items: List<FilesStore.Entry> = emptyList()

        override fun onCreate() {}

        // Runs off the main thread, so reading the folder here never blocks the home screen.
        override fun onDataSetChanged() {
            val started = SystemClock.elapsedRealtime()
            val path = FilesStore.currentPath(context, widgetId)
            items = if (FilesStore.hasAccess(context)) {
                try {
                    FilesStore.list(context, path, FilesStore.sort(context, widgetId))
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            FilesStore.recordListing(
                context, path, items.size, SystemClock.elapsedRealtime() - started
            )
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.item_file)
            val entry = items.getOrNull(position) ?: return views
            views.setImageViewResource(
                R.id.item_icon,
                if (entry.isDir) R.drawable.ic_folder_blue else R.drawable.ic_file_doc
            )
            views.setTextViewText(R.id.item_ext, entry.ext)
            views.setViewVisibility(R.id.item_ext, if (entry.isDir) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.item_name, entry.name)
            views.setTextViewText(R.id.item_sub, entry.sub)
            val fillIn = Intent().apply {
                putExtras(Bundle().apply {
                    putString(FilesStore.EXTRA_PATH, entry.path)
                    putBoolean(FilesStore.EXTRA_IS_DIR, entry.isDir)
                })
            }
            views.setOnClickFillInIntent(R.id.item_root, fillIn)
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.path?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
