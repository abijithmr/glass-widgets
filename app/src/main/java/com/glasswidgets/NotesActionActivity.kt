package com.glasswidgets

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Handles a tap on a note: the circle ticks it, the text opens the edit pop-up. Shows nothing and closes. */
class NotesActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getLongExtra(NotesStore.EXTRA_ID, -1L) ?: -1L
        if (id != -1L) {
            when (intent?.getStringExtra(NotesStore.EXTRA_KIND)) {
                NotesStore.KIND_TOGGLE -> {
                    NotesStore.toggle(this, id)
                    GlassNotesWidget.refreshAll(this)
                }
                NotesStore.KIND_EDIT -> startActivity(
                    Intent(this, NotesEditActivity::class.java).putExtra(NotesStore.EXTRA_ID, id)
                )
            }
        }
        finish()
    }
}
