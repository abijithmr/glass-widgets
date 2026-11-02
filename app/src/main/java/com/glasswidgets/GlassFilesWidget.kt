package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/**
 * Files: a dark glass window with your pinned folders on the left and the
 * contents of the current folder on the right. Taps on the rail and the back
 * button come here; taps on the grid go through FilesActionActivity.
 */
class GlassFilesWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context, id))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // Resized: switch between the labelled rail and the icon-only rail.
        appWidgetManager.partiallyUpdateAppWidget(
            appWidgetId,
            create(context, appWidgetId, withAdapter = false)
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) FilesStore.forget(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NAV -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                val path = intent.getStringExtra(FilesStore.EXTRA_PATH)
                if (id != 0 && path != null) navigate(context, id, path)
            }
            ACTION_SELECT -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                if (id != 0) {
                    FilesStore.setSelectMode(context, id, !FilesStore.selectMode(context, id))
                    redrawHeader(context, id)
                }
            }
            ACTION_SORT -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                if (id != 0) {
                    FilesStore.nextSort(context, id)
                    reload(context, id)
                }
            }
            ACTION_BACK -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                if (id != 0) {
                    val here = FilesStore.currentPath(context, id)
                    val up = FilesStore.parentOf(here)
                    if (up != here) navigate(context, id, up)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_NAV = "com.glasswidgets.files.NAV"
        const val ACTION_BACK = "com.glasswidgets.files.BACK"
        const val ACTION_SORT = "com.glasswidgets.files.SORT"
        const val ACTION_SELECT = "com.glasswidgets.files.SELECT"

        /** Below this width (dp) the rail shows icons only. */
        private const val COMPACT_BELOW_DP = 250

        private val ROW_IDS = intArrayOf(
            R.id.rail_row_0, R.id.rail_row_1, R.id.rail_row_2,
            R.id.rail_row_3, R.id.rail_row_4, R.id.rail_row_5
        )
        private val LABEL_IDS = intArrayOf(
            R.id.rail_label_0, R.id.rail_label_1, R.id.rail_label_2,
            R.id.rail_label_3, R.id.rail_label_4, R.id.rail_label_5
        )

        /** Open a folder in one widget: update the header and rail, then reload the grid. */
        fun navigate(context: Context, widgetId: Int, path: String) {
            FilesStore.setPath(context, widgetId, path)
            reload(context, widgetId)
        }

        /** Redraw the header and rail without listing the folder again. */
        fun redrawHeader(context: Context, widgetId: Int) {
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(
                widgetId,
                create(context, widgetId, withAdapter = false)
            )
        }

        /** Redraw the header and rail and list the folder again, once. */
        fun reload(context: Context, widgetId: Int) {
            FilesStore.markTapStart()
            val manager = AppWidgetManager.getInstance(context)
            manager.partiallyUpdateAppWidget(
                widgetId,
                create(context, widgetId, withAdapter = false)
            )
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.files_grid)
        }

        /** Redraw every Files widget, e.g. after the pinned folders changed. */
        fun refreshAll(context: Context, except: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlassFilesWidget::class.java))
            for (id in ids) {
                if (id == except) continue
                manager.updateAppWidget(id, create(context, id))
                manager.notifyAppWidgetViewDataChanged(id, R.id.files_grid)
            }
        }

        fun update(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(widgetId, create(context, widgetId))
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.files_grid)
        }

        /**
         * withAdapter = false skips re-attaching the grid, so a partial update only
         * touches the header and rail and the grid reloads exactly once.
         * preview = true is for the in-app library, which cannot host a live grid.
         */
        fun create(
            context: Context,
            widgetId: Int,
            withAdapter: Boolean = true,
            preview: Boolean = false
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_files)
            val pins = FilesStore.pins(context)
            val current = if (preview) (pins.firstOrNull() ?: FilesStore.root())
            else FilesStore.currentPath(context, widgetId)
            val compact = !preview && isCompact(context, widgetId)

            // Header
            views.setTextViewText(R.id.files_title, FilesStore.titleFor(current))
            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.files_back,
                    broadcast(
                        context,
                        Intent(context, GlassFilesWidget::class.java)
                            .setAction(ACTION_BACK)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        widgetId * 16 + 10
                    )
                )
            }

            val selecting = !preview && FilesStore.selectMode(context, widgetId)
            views.setTextViewText(
                R.id.files_sort_label,
                when {
                    selecting -> "Tap a file or folder"
                    preview -> FilesStore.Sort.NEWEST.label
                    else -> FilesStore.sort(context, widgetId).label
                }
            )
            views.setInt(
                R.id.files_select,
                "setBackgroundResource",
                if (selecting) R.drawable.files_rail_selected else R.drawable.files_back_bg
            )
            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.files_select,
                    broadcast(
                        context,
                        Intent(context, GlassFilesWidget::class.java)
                            .setAction(ACTION_SELECT)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        widgetId * 16 + 12
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.files_sort,
                    broadcast(
                        context,
                        Intent(context, GlassFilesWidget::class.java)
                            .setAction(ACTION_SORT)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        widgetId * 16 + 11
                    )
                )
            }

            // Rail: the folder that contains the current one is highlighted.
            val active = pins
                .filter { current == it || current.startsWith("$it/") }
                .maxByOrNull { it.length }
            val captionVisibility = if (compact) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.rail_caption, captionVisibility)
            views.setViewVisibility(R.id.rail_manage_label, captionVisibility)
            views.setViewVisibility(R.id.rail_search_label, captionVisibility)
            for (i in ROW_IDS.indices) {
                if (i >= pins.size) {
                    views.setViewVisibility(ROW_IDS[i], View.GONE)
                    continue
                }
                val pin = pins[i]
                views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                views.setTextViewText(LABEL_IDS[i], FilesStore.titleFor(pin))
                views.setViewVisibility(LABEL_IDS[i], captionVisibility)
                views.setInt(
                    ROW_IDS[i],
                    "setBackgroundResource",
                    if (pin == active) R.drawable.files_rail_selected else R.drawable.files_rail_press
                )
                if (!preview) {
                    views.setOnClickPendingIntent(
                        ROW_IDS[i],
                        broadcast(
                            context,
                            Intent(context, GlassFilesWidget::class.java)
                                .setAction(ACTION_NAV)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                .putExtra(FilesStore.EXTRA_PATH, pin),
                            widgetId * 16 + i
                        )
                    )
                }
            }
            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.rail_search,
                    Glass.activity(
                        context,
                        Intent(context, FilesSearchActivity::class.java),
                        9000 + widgetId
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.rail_manage,
                    Glass.activity(
                        context,
                        Intent(context, FilesSetupActivity::class.java),
                        7000 + widgetId
                    )
                )
            }

            // Empty state: also the "allow access" prompt.
            val hasAccess = preview || FilesStore.hasAccess(context)
            views.setTextViewText(
                R.id.files_empty,
                when {
                    preview -> "Pinned folders open here"
                    !hasAccess -> "Tap to allow file access"
                    else -> "This folder is empty"
                }
            )
            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.files_empty,
                    Glass.activity(
                        context,
                        Intent(context, FilesSetupActivity::class.java),
                        8000 + widgetId
                    )
                )
            }

            // Grid
            if (withAdapter && !preview) {
                val service = Intent(context, FilesGridService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // A unique data URI keeps each widget's grid separate.
                service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
                views.setRemoteAdapter(R.id.files_grid, service)
                views.setEmptyView(R.id.files_grid, R.id.files_empty)
                views.setPendingIntentTemplate(
                    R.id.files_grid,
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        Intent(context, FilesActionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            }
            return views
        }

        private fun isCompact(context: Context, widgetId: Int): Boolean {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            return width in 1 until COMPACT_BELOW_DP
        }

        private fun broadcast(context: Context, intent: Intent, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
