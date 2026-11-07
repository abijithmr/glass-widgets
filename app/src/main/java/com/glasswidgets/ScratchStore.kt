package com.glasswidgets

import android.content.Context

/** The text of each Scratchpad widget, kept on the phone. Every widget has its own pad. */
object ScratchStore {
    const val MAX_CHARS = 100_000
    const val EXTRA_WIDGET_ID = "scratch_widget_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("scratch_store", Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int): String =
        prefs(context).getString("text_$widgetId", "") ?: ""

    fun save(context: Context, widgetId: Int, text: String) {
        prefs(context).edit().putString("text_$widgetId", text.take(MAX_CHARS)).apply()
    }

    fun words(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    fun lines(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
}
