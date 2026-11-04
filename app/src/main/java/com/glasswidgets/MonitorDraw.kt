package com.glasswidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
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

    /** Chunky rounded blocks, the first part of them lit. */
    fun blocks(color: Int, fraction: Float?): Bitmap {
        val w = 360
        val h = 24
        val count = 20
        val gap = 4f
        val block = (w - gap * (count - 1)) / count
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lit = if (fraction == null) 0 else Math.round(count * fraction.coerceIn(0f, 100f) / 100f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            paint.color = if (i < lit) color else TRACK
            val left = i * (block + gap)
            canvas.drawRoundRect(RectF(left, 0f, left + block, h.toFloat()), 5f, 5f, paint)
        }
        return bmp
    }

    /** A filled line chart of [points] (time in ms, value) over the window ending at [now]; the scale follows the data. */
    fun trend(points: List<Pair<Long, Float>>, now: Long, spanMs: Long, color: Int): Bitmap {
        val w = 400
        val h = 130
        val pad = 8f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x14FFFFFF
        for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w.toFloat(), h * i / 4f, paint)
        if (points.isEmpty()) return bmp

        var lo = points.minOf { it.second }
        var hi = points.maxOf { it.second }
        if (hi - lo < 4f) {
            val mid = (hi + lo) / 2f
            lo = mid - 2f
            hi = mid + 2f
        }
        fun x(t: Long) = (w * (1f - (now - t).toFloat() / spanMs)).coerceIn(0f, w.toFloat())
        fun y(v: Float) = h - pad - (h - 2 * pad) * (v - lo) / (hi - lo)

        val line = Path()
        val area = Path()
        var prevT = 0L
        var open = false
        var segStartX = 0f
        for ((t, v) in points) {
            if (!open || t - prevT > 30_000L) {
                if (open) {
                    area.lineTo(x(prevT), h.toFloat()); area.lineTo(segStartX, h.toFloat()); area.close()
                }
                line.moveTo(x(t), y(v))
                area.moveTo(x(t), y(v))
                segStartX = x(t)
                open = true
            } else {
                line.lineTo(x(t), y(v))
                area.lineTo(x(t), y(v))
            }
            prevT = t
        }
        area.lineTo(x(prevT), h.toFloat()); area.lineTo(segStartX, h.toFloat()); area.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), (color and 0x00FFFFFF) or 0x80000000.toInt(), color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawPath(area, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x(points.last().first).coerceIn(6f, w - 6f), y(points.last().second), 6f, paint)
        return bmp
    }

    /** Heat blocks, 12 across by 3 down, oldest at the top left. Cool blue to warm orange on a fixed 28 to 48 °C scale. */
    fun heat(cells: List<Float?>): Bitmap {
        val cols = 12
        val rows = 3
        val w = 480
        val h = 132
        val gap = 6f
        val cw = (w - gap * (cols - 1)) / cols
        val ch = (h - gap * (rows - 1)) / rows
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until cols * rows) {
            val v = cells.getOrNull(i)
            paint.color = if (v == null) 0x1FFFFFFF else {
                val t = ((v - 28f) / 20f).coerceIn(0f, 1f)
                0xFF000000.toInt() or ((70 + (185 * t).toInt()) shl 16) or ((170 - (60 * t).toInt()) shl 8) or (230 - (170 * t).toInt())
            }
            val left = (i % cols) * (cw + gap)
            val top = (i / cols) * (ch + gap)
            canvas.drawRoundRect(RectF(left, top, left + cw, top + ch), 9f, 9f, paint)
        }
        return bmp
    }
}
