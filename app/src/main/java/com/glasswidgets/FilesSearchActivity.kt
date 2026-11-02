package com.glasswidgets

import android.app.Activity
import android.content.ContentResolver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/**
 * Search pop-up for the Files widget. It asks Android's own file index for names that
 * contain what you typed, so nothing is scanned or stored by this app.
 */
class FilesSearchActivity : Activity() {

    private class Hit(val name: String, val path: String, val parent: String, val ext: String)

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var hits: List<Hit> = emptyList()

    private lateinit var queryField: EditText
    private lateinit var status: TextView
    private lateinit var list: ListView

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = hits.size
        override fun getItem(position: Int): Any = hits[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView
                ?: LayoutInflater.from(this@FilesSearchActivity)
                    .inflate(R.layout.item_search_result, parent, false)
            val hit = hits[position]
            row.findViewById<TextView>(R.id.hit_ext).text = hit.ext
            row.findViewById<TextView>(R.id.hit_name).text = hit.name
            row.findViewById<TextView>(R.id.hit_path).text = hit.parent
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files_search)

        queryField = findViewById(R.id.search_field)
        status = findViewById(R.id.search_status)
        list = findViewById(R.id.search_list)
        list.adapter = adapter

        findViewById<View>(R.id.search_root).setOnClickListener { finish() }
        list.setOnItemClickListener { _, _, position, _ ->
            hits.getOrNull(position)?.let {
                FilesStore.openFile(this, it.path)
                finish()
            }
        }

        if (!FilesStore.hasAccess(this)) {
            status.text = "Allow file access in the Files widget setup first."
            queryField.isEnabled = false
            return
        }
        status.text = "Type at least two letters."
        queryField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                schedule(s?.toString().orEmpty().trim())
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    /** Waits a moment after the last key press so each letter doesn't start a search. */
    private fun schedule(query: String) {
        handler.removeCallbacksAndMessages(null)
        val gen = ++generation
        if (query.length < 2) {
            hits = emptyList()
            adapter.notifyDataSetChanged()
            status.text = "Type at least two letters."
            return
        }
        handler.postDelayed({
            executor.execute {
                val found = try {
                    search(query)
                } catch (e: Exception) {
                    emptyList()
                }
                runOnUiThread {
                    if (gen == generation && !isDestroyed) show(found)
                }
            }
        }, 200)
    }

    private fun show(found: List<Hit>) {
        hits = found
        adapter.notifyDataSetChanged()
        status.text = if (found.isEmpty()) "No files found." else "${found.size} found, newest first"
    }

    private fun search(query: String): List<Hit> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$escaped%"))
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, 80)
        }
        val out = ArrayList<Hit>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        contentResolver.query(
            MediaStore.Files.getContentUri("external"), projection, args, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                if (path.contains("/.")) continue
                val file = File(path)
                // Folders and files deleted since the index was updated are skipped.
                if (!file.isFile) continue
                out.add(
                    Hit(
                        name = file.name,
                        path = path,
                        parent = file.parent.orEmpty().removePrefix(FilesStore.root()).ifEmpty { "/" },
                        ext = file.extension.take(4).uppercase()
                    )
                )
            }
        }
        return out
    }
}
