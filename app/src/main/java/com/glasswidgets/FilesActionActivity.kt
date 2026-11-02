package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/**
 * Handles a tap on the Files grid, then closes. A folder opens inside the widget;
 * a file opens in whichever app the phone picks for it. It has no screen of its own.
 */
class FilesActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun handle(intent: Intent?) {
        val path = intent?.getStringExtra(FilesStore.EXTRA_PATH) ?: return
        val isDir = intent.getBooleanExtra(FilesStore.EXTRA_IS_DIR, false)
        if (isDir) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                GlassFilesWidget.navigate(this, id, path)
            }
        } else {
            FilesStore.openFile(this, path)
        }
    }
}
