package com.glasswidgets

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * A small folder browser used to pick a folder to pin. It lists real folders, so it
 * can reach Download and DCIM, which the system folder picker hides.
 */
class FolderPickerActivity : AppCompatActivity() {

    private var current: String = FilesStore.root()
    private lateinit var pathText: TextView
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var children: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)
        setResult(Activity.RESULT_CANCELED)

        pathText = findViewById(R.id.picker_path)
        list = findViewById(R.id.picker_list)
        empty = findViewById(R.id.picker_empty)

        findViewById<MaterialButton>(R.id.picker_up).setOnClickListener {
            val up = FilesStore.parentOf(current)
            if (up != current) show(up)
        }
        findViewById<MaterialButton>(R.id.picker_pin).setOnClickListener {
            setResult(Activity.RESULT_OK, Intent().putExtra(FilesStore.EXTRA_PATH, current))
            finish()
        }
        list.setOnItemClickListener { _, _, position, _ ->
            children.getOrNull(position)?.let { show(it.absolutePath) }
        }

        show(savedInstanceState?.getString(STATE_PATH) ?: FilesStore.root())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PATH, current)
    }

    private fun show(path: String) {
        current = path
        pathText.text = path
        children = (File(path).listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
        list.adapter = ArrayAdapter(
            this,
            R.layout.item_picker_row,
            R.id.picker_name,
            children.map { it.name }
        )
        empty.visibility = if (children.isEmpty()) View.VISIBLE else View.GONE
    }

    private companion object {
        const val STATE_PATH = "picker_path"
    }
}
