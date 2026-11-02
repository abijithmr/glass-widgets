package com.glasswidgets

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * State and folder listing for the Files widget: the pinned folders, the folder each
 * widget is showing, and a few timings so the speed can be judged on a real phone.
 */
object FilesStore {

    const val EXTRA_PATH = "files_path"
    const val EXTRA_IS_DIR = "files_is_dir"
    const val EXTRA_MORE = "files_more"
    const val MAX_PINS = 6

    /** The grid shows this many entries, then a "Show more" cell adds another page. */
    private const val PAGE_SIZE = 120

    private const val PREFS = "glass_files"
    private const val KEY_PINS = "pins"
    private const val KEY_PATH_PREFIX = "path_"
    private const val KEY_SORT_PREFIX = "sort_"
    private const val KEY_SELECT_PREFIX = "select_"
    private const val KEY_LIMIT_PREFIX = "limit_"
    private const val KEY_LAST_PATH = "perf_path"
    private const val KEY_LAST_COUNT = "perf_count"
    private const val KEY_LAST_LIST_MS = "perf_list_ms"
    private const val KEY_LAST_TAP_MS = "perf_tap_ms"

    /** Set when a tap starts; read when the new listing is ready. */
    @Volatile
    private var tapStartedAt = 0L

    /** How the current folder is ordered. Folders always come first. */
    enum class Sort(val label: String) {
        NEWEST("Newest first"),
        OLDEST("Oldest first"),
        NAME("Name A\u2013Z"),
        LARGEST("Largest first")
    }

    data class Entry(
        val name: String,
        val path: String,
        val isDir: Boolean,
        val sub: String,
        val ext: String,
        /** The last cell of a long folder: tapping it shows the next page. */
        val isMore: Boolean = false
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun root(): String = Environment.getExternalStorageDirectory().absolutePath

    /** True when the app may read ordinary folders such as Download and DCIM. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    // ---- pinned folders ----

    fun pins(context: Context): List<String> {
        val p = prefs(context)
        if (!p.contains(KEY_PINS)) {
            // First run: start with Downloads so the widget is not blank.
            val downloads = File(root(), "Download")
            val seed = if (downloads.isDirectory) downloads.absolutePath else ""
            p.edit().putString(KEY_PINS, seed).apply()
        }
        return (p.getString(KEY_PINS, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .take(MAX_PINS)
    }

    private fun savePins(context: Context, pins: List<String>) {
        prefs(context).edit().putString(KEY_PINS, pins.joinToString("\n")).apply()
    }

    /** Returns false when the folder is already pinned or the list is full. */
    fun addPin(context: Context, path: String): Boolean {
        val pins = pins(context)
        if (pins.contains(path) || pins.size >= MAX_PINS) return false
        savePins(context, pins + path)
        return true
    }

    fun removePin(context: Context, path: String) {
        savePins(context, pins(context).filter { it != path })
    }

    // ---- the folder each widget shows ----

    fun currentPath(context: Context, widgetId: Int): String {
        val saved = prefs(context).getString(KEY_PATH_PREFIX + widgetId, null)
        if (saved != null && File(saved).isDirectory) return saved
        return pins(context).firstOrNull() ?: root()
    }

    fun setPath(context: Context, widgetId: Int, path: String) {
        prefs(context).edit()
            .putString(KEY_PATH_PREFIX + widgetId, path)
            .remove(KEY_LIMIT_PREFIX + widgetId)
            .apply()
    }

    /** How many entries this widget shows right now. */
    fun limit(context: Context, widgetId: Int): Int =
        prefs(context).getInt(KEY_LIMIT_PREFIX + widgetId, PAGE_SIZE)

    fun showMore(context: Context, widgetId: Int) {
        prefs(context).edit()
            .putInt(KEY_LIMIT_PREFIX + widgetId, limit(context, widgetId) + PAGE_SIZE)
            .apply()
    }

    fun sort(context: Context, widgetId: Int): Sort {
        val saved = prefs(context).getInt(KEY_SORT_PREFIX + widgetId, 0)
        return Sort.values().getOrElse(saved) { Sort.NEWEST }
    }

    /** Move this widget to the next sort order and return it. */
    fun nextSort(context: Context, widgetId: Int): Sort {
        val all = Sort.values()
        val next = all[(sort(context, widgetId).ordinal + 1) % all.size]
        prefs(context).edit()
            .putInt(KEY_SORT_PREFIX + widgetId, next.ordinal)
            .remove(KEY_LIMIT_PREFIX + widgetId)
            .apply()
        return next
    }

    /** When on, the next tap on an item opens its actions instead of opening it. */
    fun selectMode(context: Context, widgetId: Int): Boolean =
        prefs(context).getBoolean(KEY_SELECT_PREFIX + widgetId, false)

    fun setSelectMode(context: Context, widgetId: Int, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SELECT_PREFIX + widgetId, on).apply()
    }

    /**
     * A folder was renamed (newPath) or deleted (newPath = null): keep the pinned
     * list pointing at real folders.
     */
    fun pathChanged(context: Context, oldPath: String, newPath: String?) {
        val updated = pins(context).mapNotNull { pin ->
            when {
                pin == oldPath || pin.startsWith("$oldPath/") ->
                    newPath?.let { it + pin.removePrefix(oldPath) }
                else -> pin
            }
        }
        savePins(context, updated)
    }

    fun forget(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(KEY_PATH_PREFIX + widgetId)
            .remove(KEY_SORT_PREFIX + widgetId)
            .remove(KEY_SELECT_PREFIX + widgetId)
            .remove(KEY_LIMIT_PREFIX + widgetId)
            .apply()
    }

    /** One step up, but never above the phone's main storage. */
    fun parentOf(path: String): String {
        val root = root()
        if (path == root || !path.startsWith(root)) return path
        val parent = File(path).parentFile ?: return path
        return if (parent.absolutePath.startsWith(root)) parent.absolutePath else root
    }

    fun titleFor(path: String): String =
        if (path == root()) "Internal storage" else File(path).name

    // ---- listing ----

    private class Item(val file: File, val isDir: Boolean, val modified: Long, val size: Long)

    fun list(context: Context, path: String, sort: Sort, limit: Int = PAGE_SIZE): List<Entry> {
        val children = File(path).listFiles() ?: return emptyList()
        // Read each modified time and size once instead of on every comparison.
        val items = children
            .filter { !it.name.startsWith(".") }
            .map {
                val dir = it.isDirectory
                Item(it, dir, it.lastModified(), if (dir) 0L else it.length())
            }
        val byName = compareBy<Item> { it.file.name.lowercase() }
        val order: Comparator<Item> = when (sort) {
            Sort.NEWEST -> compareByDescending<Item> { it.modified }
            Sort.OLDEST -> compareBy<Item> { it.modified }
            Sort.NAME -> byName
            // Folders have no cheap size, so they stay in name order above the files.
            Sort.LARGEST -> compareByDescending<Item> { it.size }.then(byName)
        }
        val ordered = items.sortedWith(compareByDescending<Item> { it.isDir }.then(order))
        val sorted = ordered.take(limit)
        val remaining = ordered.size - sorted.size

        return sorted.map { item ->
            val file = item.file
            val isDir = item.isDir
            if (isDir) {
                val count = file.list()?.size ?: 0
                Entry(
                    name = file.name,
                    path = file.absolutePath,
                    isDir = true,
                    sub = if (count == 1) "1 item" else "$count items",
                    ext = ""
                )
            } else {
                Entry(
                    name = file.name,
                    path = file.absolutePath,
                    isDir = false,
                    sub = Formatter.formatShortFileSize(context, item.size),
                    ext = file.extension.take(4).uppercase()
                )
            }
        } + if (remaining > 0) {
            listOf(
                Entry(
                    name = "Show more",
                    path = "",
                    isDir = false,
                    sub = "$remaining more",
                    ext = "",
                    isMore = true
                )
            )
        } else {
            emptyList()
        }
    }

    /** Opens a file in whichever app the phone picks for its type. */
    fun openFile(context: Context, path: String) {
        val file = File(path)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "Can't open this file", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- timings ----

    fun markTapStart() {
        tapStartedAt = SystemClock.elapsedRealtime()
    }

    fun recordListing(context: Context, path: String, count: Int, listMs: Long) {
        val started = tapStartedAt
        tapStartedAt = 0L
        val edit = prefs(context).edit()
            .putString(KEY_LAST_PATH, path)
            .putInt(KEY_LAST_COUNT, count)
            .putLong(KEY_LAST_LIST_MS, listMs)
        if (started > 0L) {
            edit.putLong(KEY_LAST_TAP_MS, SystemClock.elapsedRealtime() - started)
        }
        edit.apply()
    }

    fun perfReport(context: Context): String {
        val p = prefs(context)
        val path = p.getString(KEY_LAST_PATH, null)
            ?: return "Nothing measured yet. Open a folder in the widget, then come back here."
        val tap = p.getLong(KEY_LAST_TAP_MS, -1L)
        return buildString {
            append("Last folder   ").append(titleFor(path)).append('\n')
            append("Entries       ").append(p.getInt(KEY_LAST_COUNT, 0)).append('\n')
            append("Read + sort   ").append(p.getLong(KEY_LAST_LIST_MS, 0)).append(" ms\n")
            append("Tap to listed ")
            append(if (tap >= 0) "$tap ms" else "n/a (rail or back taps only)")
            append("\n\nThe last line stops when the list is ready. The launcher then draws it, which is not counted.")
        }
    }
}
