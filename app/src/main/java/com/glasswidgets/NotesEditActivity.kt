package com.glasswidgets

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView

/** Pop-up for one note: change its text or delete it. */
class NotesEditActivity : Activity() {

    private var noteId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteId = intent?.getLongExtra(NotesStore.EXTRA_ID, -1L) ?: -1L
        val note = NotesStore.get(this, noteId)
        if (note == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_notes_edit)
        val field = findViewById<EditText>(R.id.note_field)
        val status = findViewById<TextView>(R.id.note_status)
        field.setText(note.text)
        field.setSelection(field.text.length)
        findViewById<android.view.View>(R.id.note_root).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.note_delete).setOnClickListener {
            NotesStore.delete(this, noteId)
            GlassNotesWidget.refreshAll(this)
            finish()
        }
        findViewById<android.view.View>(R.id.note_save).setOnClickListener {
            if (NotesStore.rename(this, noteId, field.text.toString())) {
                GlassNotesWidget.refreshAll(this)
                finish()
            } else {
                status.text = "A note can't be empty. Delete it instead."
            }
        }
    }
}
