package com.glasswidgets

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/**
 * Opens Android's own Wi-Fi panel (the sheet with the Wi-Fi switch and network list) on top of
 * the home screen, then closes at once. Falls back to the Wi-Fi settings page if the phone
 * doesn't have the panel.
 */
class PanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = if (Build.VERSION.SDK_INT >= 29) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        try {
            startActivity(panel)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (_: ActivityNotFoundException) {
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
