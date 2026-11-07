package com.glasswidgets

import android.content.Context
import android.graphics.Typeface

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

    /** Colours of one mode. [accent] is the teal button; [onAccent] is the text on it. */
    class Palette(
        val page: Int, val strip: Int, val line: Int, val text: Int, val sub: Int, val hint: Int,
        val ghostFill: Int, val ghostStroke: Int, val accent: Int, val onAccent: Int
    )

    private val LIGHT = Palette(
        0xFFFFFFFF.toInt(), 0xFFF4F6F9.toInt(), 0xFFE1E6EE.toInt(), 0xFF1D2330.toInt(), 0xFF7A849A.toInt(),
        0xFF9AA3B5.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFD6E1.toInt(), 0xFF0F9D94.toInt(), 0xFFFFFFFF.toInt()
    )
    private val DARK = Palette(
        0xFF14171D.toInt(), 0xFF1C2028.toInt(), 0xFF2A303B.toInt(), 0xFFE8ECF3.toInt(), 0xFF8B93A7.toInt(),
        0xFF5E667A.toInt(), 0xFF252A34.toInt(), 0xFF3A4150.toInt(), 0xFF3FD0C6.toInt(), 0xFF07201F.toInt()
    )

    fun palette(dark: Boolean): Palette = if (dark) DARK else LIGHT

    /** Dark or light, for every Scratchpad widget and the editor. */
    fun dark(context: Context): Boolean = prefs(context).getBoolean("dark", false)

    fun setDark(context: Context, dark: Boolean) {
        prefs(context).edit().putBoolean("dark", dark).apply()
    }

    /** The fonts on offer. The first two are the phone's own; the rest are bundled free fonts. */
    val FONT_NAMES = listOf(
        "Monospace", "Sans", "JetBrains Mono", "Atkinson Hyperlegible", "Lora", "Special Elite", "Caveat"
    )

    fun font(context: Context): Int =
        prefs(context).getInt("font", 0).coerceIn(0, FONT_NAMES.size - 1)

    fun setFont(context: Context, index: Int) {
        prefs(context).edit().putInt("font", index).apply()
    }

    fun typeface(context: Context, index: Int): Typeface = try {
        when (index) {
            1 -> Typeface.SANS_SERIF
            2 -> context.resources.getFont(R.font.jetbrains_mono)
            3 -> context.resources.getFont(R.font.atkinson_hyperlegible)
            4 -> context.resources.getFont(R.font.lora)
            5 -> context.resources.getFont(R.font.special_elite)
            6 -> context.resources.getFont(R.font.caveat)
            else -> Typeface.MONOSPACE
        }
    } catch (e: Exception) {
        Typeface.MONOSPACE
    }

    fun words(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    fun lines(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
}
