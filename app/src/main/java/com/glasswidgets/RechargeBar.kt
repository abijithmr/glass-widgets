package com.glasswidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** Draws the big glass battery bar: a dark track with a glowing fill that follows the charge. */
object RechargeBar {

    private const val W = 480
    private const val H = 80

    fun draw(level: Int, charging: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = H / 2f

        // Track
        val track = RectF(2f, 2f, W - 2f, H - 2f)
        canvas.drawRoundRect(track, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66101826 })
        canvas.drawRoundRect(track, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x66FFFFFF
        })

        // Fill: red when low, orange in the middle, green when charging or high
        val (from, to) = when {
            charging || level > 50 -> 0xFF3FBF73.toInt() to 0xFF9BE8A8.toInt()
            level > 20 -> 0xFFFF8A2B.toInt() to 0xFFFFC46B.toInt()
            else -> 0xFFE5341F.toInt() to 0xFFFF7A4A.toInt()
        }
        val inset = 7f
        val fillWidth = ((W - 2 * inset) * level.coerceIn(0, 100) / 100f)
        if (fillWidth > 0f) {
            val rect = RectF(inset, inset, inset + fillWidth.coerceAtLeast(H - 2 * inset), H - inset)
            val fillR = (H - 2 * inset) / 2f
            canvas.drawRoundRect(rect, fillR, fillR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(rect.left, 0f, rect.right, 0f, from, to, Shader.TileMode.CLAMP)
            })
            // Soft highlight along the top edge
            val gloss = Path().apply {
                addRoundRect(
                    RectF(rect.left + 6f, rect.top + 4f, rect.right - 6f, rect.top + (rect.height() * 0.38f)),
                    fillR, fillR, Path.Direction.CW
                )
            }
            canvas.drawPath(gloss, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF })
        }
        return bmp
    }
}
