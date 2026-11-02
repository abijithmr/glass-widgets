package com.glasswidgets

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.text.format.Formatter
import java.io.File

/**
 * State and folder listing for the Files widget: the pinned folders, the folder each
 * widget is showing, and a few timings so the speed can be judged on a real phone.
 */
object FilesStore {

    const val EXTRA_PATH = "files_path"
    const val EXTRA_IS_DIR = "files_is_dir"
    const val MAX_PINS = 6

    /** The list shows the newest entries first and stops here (Phase 1 limit). */
    private const val MAX_ENTRIES = 120

    private const val PREFS = "glass_files"
    private const val KEY_PINS = "pins"
    private const val KEY_PATH_PREFIX = "path_"
    private const val KEY_LAST_PATH = "perf_path"
    private const val KEY_LAST_COUNT = "perf_count"
    private const val KEY_LAST_LIST_MS = "perf_list_ms"
    private const val KEY_LAST_TAP_MS = "perf_tap_ms"

    /** Set when a tap starts; read when the new listing is ready. */
    @Volatile
    private var tapStartedAt = 0L

    data class Entry(
        val name: String,
        val path: String,
        val isDir: Boolean,
        val sub: String,
        val ext: String,
        val modified: Long
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
        prefs(context).edit().putString(KEY_PATH_PREFIX + widgetId, path).apply()
    }

    fun forget(context: Context, widgetId: Int) {
        prefs(context).edit().remove(KEY_PATH_PREFIX + widgetId).apply()
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

    fun list(context: Context, path: String): List<Entry> {
        val children = File(path).listFiles() ?: return emptyList()
        // Read each modified time once instead of on every comparison.
        val stamped = children
            .filter { !it.name.startsWith(".") }
            .map { Triple(it, it.isDirectory, it.lastModified()) }
        // Newest first, folders and files together, so the date headings read naturally.
        val sorted = stamped.sortedByDescending { it.third }.take(MAX_ENTRIES)

        return sorted.map { (file, isDir, modified) ->
            if (isDir) {
                val count = file.list()?.size ?: 0
                Entry(
                    name = file.name,
                    path = file.absolutePath,
                    isDir = true,
                    sub = if (count == 1) "1 item" else "$count items",
                    ext = "",
                    modified = modified
                )
            } else {
                Entry(
                    name = file.name,
                    path = file.absolutePath,
                    isDir = false,
                    sub = Formatter.formatShortFileSize(context, file.length()),
                    ext = file.extension.take(4).uppercase(),
                    modified = modified
                )
            }
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
