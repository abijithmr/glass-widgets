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

    /** One vertical bar per CPU core, filled to that core's current clock, with a label under it. */
    fun coreBars(fractions: List<Float?>): Bitmap {
        val w = 360
        val h = 132
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val n = fractions.size.coerceAtLeast(1)
        val gap = 10f
        val barW = (w - gap * (n - 1)) / n
        val barH = h - 30f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBFFFFFFF.toInt()
            textSize = 15f
            textAlign = Paint.Align.CENTER
        }
        for (i in 0 until n) {
            val left = i * (barW + gap)
            paint.shader = null
            paint.color = 0x1FFFFFFF
            canvas.drawRoundRect(RectF(left, 0f, left + barW, barH), 10f, 10f, paint)
            val f = fractions.getOrNull(i)
            if (f != null && f > 0f) {
                val top = barH - barH * f.coerceIn(0f, 100f) / 100f
                paint.shader = LinearGradient(0f, barH, 0f, top, 0xFF5CC8FF.toInt(), 0xFF9BE0FF.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(left, top.coerceAtMost(barH - 12f), left + barW, barH), 10f, 10f, paint)
            }
            canvas.drawText("CPU$i", left + barW / 2f, h - 4f, label)
        }
        return bmp
    }

    /** The Glass Widgets mark: a glass tile with a glass disc in front. */
    fun logo(): Bitmap {
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = 0x47FFFFFF
        canvas.drawRoundRect(RectF(8f, 14f, 76f, 82f), 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x80FFFFFF.toInt()
        canvas.drawRoundRect(RectF(8f, 14f, 76f, 82f), 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0x6BFFFFFF
        canvas.drawCircle(78f, 78f, 32f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = 0xB3FFFFFF.toInt()
        canvas.drawCircle(78f, 78f, 32f, paint)
        return bmp
    }

    /** A row of round dots, the first part of them lit. */
    fun dots(fraction: Float?, lit: Int): Bitmap {
        val w = 360
        val h = 22
        val count = 24
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val on = if (fraction == null) 0 else Math.round(count * fraction.coerceIn(0f, 100f) / 100f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val step = w / count.toFloat()
        for (i in 0 until count) {
            paint.color = if (i < on) lit else 0x29FFFFFF
            canvas.drawCircle(step * i + step / 2f, h / 2f, 6f, paint)
        }
        return bmp
    }
}
