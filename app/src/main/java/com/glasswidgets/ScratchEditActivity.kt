package com.glasswidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Scratchpad editor: a plain white page. The text is kept as you type. "Save as…" opens
 * Android's own save screen, where you pick the folder and the file name (.txt or .text).
 */
class ScratchEditActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var field: ScratchEditText
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val flush = Runnable { persist() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(ScratchStore.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setContentView(R.layout.activity_scratch)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        field = findViewById(R.id.scratch_field)
        status = findViewById(R.id.scratch_status)
        field.filters = arrayOf(InputFilter.LengthFilter(ScratchStore.MAX_CHARS))
        field.setText(ScratchStore.load(this, widgetId))
        field.setSelection(field.text.length)
        field.onCursorMoved = { updateStatus() }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Saved shortly after you stop typing, and always when you leave.
                handler.removeCallbacks(flush)
                handler.postDelayed(flush, 500)
                updateStatus()
            }
        })
        updateStatus()

        findViewById<View>(R.id.scratch_back).setOnClickListener { finish() }
        findViewById<View>(R.id.scratch_share).setOnClickListener { share() }
        findViewById<View>(R.id.scratch_save).setOnClickListener { saveAs() }
        findViewById<View>(R.id.scratch_more).setOnClickListener { showMenu(it) }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(flush)
        persist()
        GlassScratchWidget.refreshAll(this)
    }

    private fun persist() {
        ScratchStore.save(this, widgetId, field.text.toString())
    }

    private fun updateStatus() {
        val text = field.text
        val pos = field.selectionStart.coerceIn(0, text.length)
        val before = text.subSequence(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - before.lastIndexOf('\n')
        status.text = "Ln $line, Col $col · ${text.length} characters · saved"
    }

    private fun share() {
        val text = field.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "Share note"
            )
        )
    }

    private fun saveAs() {
        if (field.text.isBlank()) {
            Toast.makeText(this, "Write something first", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "note-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".txt"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, name)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SAVE)
        } catch (e: Exception) {
            Toast.makeText(this, "No save screen found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(field.text.toString().toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("no stream")
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save the file", Toast.LENGTH_LONG).show()
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "New note")
        menu.setOnMenuItemClickListener {
            if (it.itemId == 1) newNote()
            true
        }
        menu.show()
    }

    private fun newNote() {
        if (field.text.isBlank()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear this note?")
            .setMessage("The note will be emptied. Use Save as… first if you want to keep it.")
            .setPositiveButton("Clear") { _, _ -> field.setText("") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val REQUEST_SAVE = 41
    }
}
