package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

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
            openFile(path)
        }
    }

    private fun openFile(path: String) {
        val file = File(path)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Can't open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
