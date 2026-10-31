package com.glasswidgets

import android.content.Context
import android.provider.Settings

/** Three screen brightness levels for the Controls tile. Reading needs no permission. */
object BrightnessController {

    const val LOW = 0
    const val MID = 1
    const val HIGH = 2

    private val VALUES = intArrayOf(20, 128, 255)

    /** The level closest to what the screen is set to right now. */
    fun level(context: Context): Int {
        val value = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
        return when {
            value < 70 -> LOW
            value < 192 -> MID
            else -> HIGH
        }
    }

    fun icon(level: Int): Int = when (level) {
        LOW -> R.drawable.ic_brightness_low
        MID -> R.drawable.ic_brightness_mid
        else -> R.drawable.ic_brightness_high
    }

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Low -> mid -> peak -> low. Switches off auto-brightness so the level sticks. */
    fun cycle(context: Context) {
        val next = (level(context) + 1) % 3
        val resolver = context.contentResolver
        Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, VALUES[next])
    }
}
