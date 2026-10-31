package com.glasswidgets

import android.graphics.drawable.GradientDrawable

/** The two choices that personalise the app: a preview backdrop and the "today" highlight colour. */
object Look {

    class Scene(val name: String, val colors: IntArray)

    class Accent(val name: String, val color: Int, val pillRes: Int)

    val scenes: List<Scene> = listOf(
        Scene("Dusk", intArrayOf(0xFF2A1B55.toInt(), 0xFFB04A6E.toInt(), 0xFFF6A56B.toInt())),
        Scene("Ocean", intArrayOf(0xFF082B45.toInt(), 0xFF2F83B8.toInt(), 0xFF9ED8D8.toInt())),
        Scene("Forest", intArrayOf(0xFF0C2A20.toInt(), 0xFF2E6A4D.toInt(), 0xFFB5D26A.toInt())),
        Scene("Graphite", intArrayOf(0xFF111318.toInt(), 0xFF383E48.toInt(), 0xFF8E96A3.toInt()))
    )

    val accents: List<Accent> = listOf(
        Accent("White", 0xFFF2F2F2.toInt(), R.drawable.glass_day_pill),
        Accent("Sky", 0xFF9AD7FF.toInt(), R.drawable.glass_day_pill_sky),
        Accent("Mint", 0xFF9BF0C8.toInt(), R.drawable.glass_day_pill_mint),
        Accent("Peach", 0xFFFFC59A.toInt(), R.drawable.glass_day_pill_peach),
        Accent("Rose", 0xFFFFA3C0.toInt(), R.drawable.glass_day_pill_rose),
        Accent("Lilac", 0xFFC9B2FF.toInt(), R.drawable.glass_day_pill_lilac)
    )

    /** A rounded gradient standing in for the user's wallpaper. */
    fun sceneDrawable(scene: Scene, radiusPx: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, scene.colors).apply {
            cornerRadius = radiusPx
        }
}
