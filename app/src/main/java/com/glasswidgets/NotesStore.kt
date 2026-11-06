package com.glasswidgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The notes list behind the Notes widget: one list on the phone, shared by every Notes widget. */
object NotesStore {
    class Note(val id: Long, val text: String, val done: Boolean)

    const val MAX_NOTES = 200
    const val MAX_LENGTH = 300
    const val EXTRA_ID = "note_id"
    const val EXTRA_KIND = "note_kind"
    const val KIND_TOGGLE = "toggle"
    const val KIND_EDIT = "edit"

    private const val PREFS = "notes_store"
    private const val KEY = "items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun load(context: Context): MutableList<Note> {
        val list = mutableListOf<Note>()
        try {
            val array = JSONArray(prefs(context).getString(KEY, "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(Note(o.getLong("id"), o.getString("t"), o.optBoolean("d", false)))
            }
        } catch (e: Exception) {
            // A damaged list starts empty rather than crashing the widget.
        }
        return list
    }

    @Synchronized
    private fun save(context: Context, list: List<Note>) {
        val array = JSONArray()
        for (n in list) {
            array.put(JSONObject().put("id", n.id).put("t", n.text).put("d", n.done))
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    /** To-do notes first (newest on top), then the ticked ones (newest on top). */
    fun all(context: Context): List<Note> {
        val list = load(context)
        return list.filter { !it.done }.sortedByDescending { it.id } +
            list.filter { it.done }.sortedByDescending { it.id }
    }

    fun get(context: Context, id: Long): Note? = load(context).firstOrNull { it.id == id }

    fun openCount(context: Context): Int = load(context).count { !it.done }

    fun total(context: Context): Int = load(context).size

    fun doneCount(context: Context): Int = load(context).count { it.done }

    /** Adds a note; false when it is blank or the list is full. */
    @Synchronized
    fun add(context: Context, text: String): Boolean {
        val clean = text.trim().take(MAX_LENGTH)
        val list = load(context)
        if (clean.isEmpty() || list.size >= MAX_NOTES) return false
        val id = maxOf(System.currentTimeMillis(), (list.maxOfOrNull { it.id } ?: 0L) + 1)
        list.add(Note(id, clean, false))
        save(context, list)
        return true
    }

    @Synchronized
    fun toggle(context: Context, id: Long) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = Note(id, list[i].text, !list[i].done)
        save(context, list)
    }

    @Synchronized
    fun rename(context: Context, id: Long, text: String): Boolean {
        val clean = text.trim().take(MAX_LENGTH)
        if (clean.isEmpty()) return false
        val list = load(context)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return false
        list[i] = Note(id, clean, list[i].done)
        save(context, list)
        return true
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        save(context, load(context).filter { it.id != id })
    }

    @Synchronized
    fun clearDone(context: Context) {
        save(context, load(context).filter { !it.done })
    }
}
