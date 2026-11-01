package com.glasswidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** Remembers the real readings the monitor has taken and draws them like a resource monitor. */
object MonitorGraph {

    private const val PREFS = "monitor_history"
    private const val MAX_POINTS = 30
    private const val MIN_GAP_MS = 60_000L

    private const val W = 180
    private const val H = 72

    /** Add this reading to the history, at most once a minute. */
    fun record(context: Context, name: String, value: Float?) {
        if (value == null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("t_$name", 0L) < MIN_GAP_MS) return
        val list = history(context, name).toMutableList()
        list.add(value)
        while (list.size > MAX_POINTS) list.removeAt(0)
        prefs.edit()
            .putString("h_$name", list.joinToString(","))
            .putLong("t_$name", now)
            .apply()
    }

    fun history(context: Context, name: String): List<Float> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("h_$name", null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.toFloatOrNull() }
    }

    /**
     * Tile background: a history line (levels that move: CPU, temperatures) or a level bar
     * (things that fill up: battery, memory, storage).
     */
    fun draw(color: Int, history: List<Float>, current: Float?, asBar: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = 14f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Tile body
        canvas.drawColor(0x40081220)

        // Grid, like a resource monitor
        val grid = Paint().apply { this.color = 0x1AFFFFFF; strokeWidth = 1f }
        for (i in 1..3) canvas.drawLine(0f, H * i / 4f, W.toFloat(), H * i / 4f, grid)
        for (i in 1..5) canvas.drawLine(W * i / 6f, 0f, W * i / 6f, H.toFloat(), grid)

        if (current == null) return bmp

        if (asBar) {
            val x = W * current.coerceIn(0f, 100f) / 100f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, x.coerceAtLeast(1f), 0f, withAlpha(color, 0.18f), withAlpha(color, 0.50f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, x, H.toFloat(), fill)
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 3f }
            canvas.drawLine(x, 0f, x, H.toFloat(), edge)
            return bmp
        }

        val points = if (history.isEmpty()) listOf(current) else history
        val step = W / (MAX_POINTS - 1f)
        val left = W - step * (points.size - 1)
        val top = 8f
        val bottom = H - 4f
        fun y(v: Float) = bottom - (bottom - top) * v.coerceIn(0f, 100f) / 100f

        val line = Path()
        points.forEachIndexed { i, v ->
            val px = left + step * i
            if (i == 0) line.moveTo(px, y(v)) else line.lineTo(px, y(v))
        }
        if (points.size == 1) line.lineTo(W.toFloat(), y(points[0]))

        val area = Path(line).apply {
            lineTo(W.toFloat(), H.toFloat())
            lineTo(if (points.size == 1) 0f else left, H.toFloat())
            close()
        }
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), withAlpha(color, 0.55f), withAlpha(color, 0.04f), Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
}
