package com.glasswidgets

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/** Typing pop-up for new notes. It stays open after each Add so several can be entered in a row. */
class NotesAddActivity : Activity() {

    private lateinit var field: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes_add)
        field = findViewById(R.id.note_field)
        status = findViewById(R.id.note_status)
        findViewById<android.view.View>(R.id.note_root).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.note_done).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.note_add_btn).setOnClickListener { add() }
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                add()
                true
            } else {
                false
            }
        }
    }

    private fun add() {
        val text = field.text.toString()
        if (text.isBlank()) {
            status.text = "Type a note first"
            return
        }
        if (NotesStore.add(this, text)) {
            field.setText("")
            status.text = "Added. ${NotesStore.openCount(this)} left."
            GlassNotesWidget.refreshAll(this)
        } else {
            status.text = "The list is full (${NotesStore.MAX_NOTES} notes). Clear some first."
        }
    }
}
