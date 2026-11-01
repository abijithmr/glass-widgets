package com.glasswidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Small bitmaps for the System Monitor looks: gauge rings and bars. */
object MonitorDraw {

    private const val TRACK = 0x2AFFFFFF

    /** A ring gauge, filled clockwise from the top. */
    fun ring(color: Int, fraction: Float?): Bitmap {
        val size = 120
        val stroke = 11f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val box = RectF(stroke, stroke, size - stroke, size - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = TRACK
        canvas.drawArc(box, 0f, 360f, false, paint)
        if (fraction != null && fraction > 0f) {
            paint.color = color
            canvas.drawArc(box, -90f, 360f * fraction.coerceIn(1f, 100f) / 100f, false, paint)
        }
        return bmp
    }

    /** A terminal-style bar made of small segments. */
    fun segments(color: Int, fraction: Float?): Bitmap {
        val w = 252
        val h = 20
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val count = 28
        val gap = 3f
        val seg = (w - gap * (count - 1)) / count
        val lit = if (fraction == null) 0 else Math.round(count * fraction.coerceIn(0f, 100f) / 100f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            paint.color = if (i < lit) color else TRACK
            val left = i * (seg + gap)
            canvas.drawRoundRect(RectF(left, 2f, left + seg, h - 2f), 2f, 2f, paint)
        }
        return bmp
    }

    /** A slim rounded bar with a colour gradient. */
    fun bar(from: Int, to: Int, fraction: Float?): Bitmap {
        val w = 300
        val h = 16
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = h / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = TRACK
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        if (fraction != null && fraction > 0f) {
            val width = (w * fraction.coerceIn(0f, 100f) / 100f).coerceAtLeast(h.toFloat())
            paint.shader = LinearGradient(0f, 0f, width, 0f, from, to, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(0f, 0f, width, h.toFloat()), r, r, paint)
        }
        return bmp
    }
}
