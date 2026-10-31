package com.glasswidgets

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Invisible screen behind the brightness tile. The first tap opens Android's "Modify system
 * settings" screen; after that each tap steps low -> mid -> peak.
 */
class BrightnessActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (BrightnessController.canWrite(this)) {
                BrightnessController.cycle(this)
                Glass.refresh(applicationContext, GlassControlsWidget::class.java)
            } else {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("GlassWidgets", "Brightness tap failed", e)
        }
        finish()
    }
}
