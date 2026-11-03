package com.glasswidgets

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Pop-up for one file or folder, opened by tapping it in select mode: share,
 * rename, copy its path or delete it. Rename and delete happen right here, and
 * delete asks first because there is no bin.
 */
class FilesItemActivity : Activity() {

    private lateinit var file: File
    private var isDir = false

    private lateinit var panelActions: View
    private lateinit var panelRename: View
    private lateinit var panelDelete: View
    private lateinit var renameField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.getStringExtra(FilesStore.EXTRA_PATH)
        if (path == null || !File(path).exists()) {
            finish()
            return
        }
        file = File(path)
        isDir = file.isDirectory
        setContentView(R.layout.activity_files_item)

        findViewById<TextView>(R.id.item_title).text = file.name
        findViewById<TextView>(R.id.item_subtitle).text =
            if (isDir) "Folder" else Formatter.formatShortFileSize(this, file.length())

        panelActions = findViewById(R.id.panel_actions)
        panelRename = findViewById(R.id.panel_rename)
        panelDelete = findViewById(R.id.panel_delete)
        renameField = findViewById(R.id.rename_field)

        findViewById<View>(R.id.item_root).setOnClickListener { finish() }

        val share = findViewById<View>(R.id.act_share)
        // Only files can be shared.
        share.visibility = if (isDir) View.GONE else View.VISIBLE
        share.setOnClickListener { share() }
        findViewById<View>(R.id.act_copy).setOnClickListener { copyPath() }
        findViewById<View>(R.id.act_rename).setOnClickListener { showRename() }
        findViewById<View>(R.id.act_delete).setOnClickListener { showDelete() }

        findViewById<View>(R.id.rename_cancel).setOnClickListener { showActions() }
        findViewById<View>(R.id.rename_save).setOnClickListener { saveRename() }
        findViewById<View>(R.id.delete_cancel).setOnClickListener { showActions() }
        findViewById<View>(R.id.delete_confirm).setOnClickListener { confirmDelete() }
    }

    // This pop-up is a single instance. If it is still open in the background when another
    // item is chosen, the new item arrives here: start over so rename and delete can never
    // act on the earlier item.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun showActions() {
        panelRename.visibility = View.GONE
        panelDelete.visibility = View.GONE
        panelActions.visibility = View.VISIBLE
    }

    private fun share() {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(file.name, uri)
            startActivity(
                Intent.createChooser(send, "Share ${file.name}")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        } catch (e: Exception) {
            toast("Can't share this file")
        }
    }

    private fun copyPath() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("path", file.absolutePath))
        toast("Path copied")
        finish()
    }

    // ---- rename ----

    private fun showRename() {
        panelActions.visibility = View.GONE
        panelRename.visibility = View.VISIBLE
        renameField.setText(file.name)
        // Select the name but not the extension, so ".pdf" survives a quick edit.
        val dot = file.name.lastIndexOf('.')
        renameField.setSelection(0, if (!isDir && dot > 0) dot else file.name.length)
        renameField.requestFocus()
        renameField.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(renameField, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun saveRename() {
        val name = renameField.text.toString().trim()
        when {
            name.isEmpty() || name.contains('/') -> toast("Enter a valid name")
            name == file.name -> finish()
            else -> {
                val target = File(file.parentFile, name)
                when {
                    target.exists() -> toast("That name is already used")
                    file.renameTo(target) -> {
                        if (isDir) FilesStore.pathChanged(this, file.absolutePath, target.absolutePath)
                        changed(arrayOf(file.absolutePath, target.absolutePath), "Renamed")
                    }
                    else -> toast("Couldn't rename")
                }
            }
        }
    }

    // ---- delete ----

    private fun showDelete() {
        panelActions.visibility = View.GONE
        panelDelete.visibility = View.VISIBLE
        findViewById<TextView>(R.id.delete_message).text =
            if (isDir) "Delete this folder and everything in it? This can't be undone."
            else "Delete this file? This can't be undone."
    }

    private fun confirmDelete() {
        val ok = if (isDir) file.deleteRecursively() else file.delete()
        if (ok) {
            if (isDir) FilesStore.pathChanged(this, file.absolutePath, null)
            changed(arrayOf(file.absolutePath), "Deleted")
        } else {
            toast("Couldn't delete it")
        }
    }

    /** Tell the media index and every Files widget that something moved or went away. */
    private fun changed(paths: Array<String>, message: String) {
        MediaScannerConnection.scanFile(applicationContext, paths, null, null)
        GlassFilesWidget.refreshAll(this)
        toast(message)
        finish()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
