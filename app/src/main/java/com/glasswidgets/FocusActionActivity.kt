package com.glasswidgets

import android.app.Activity
import android.os.Bundle

/**
 * Invisible screen that runs a Focus tap and closes at once. Using an activity
 * (not a broadcast) lets it open the "Do Not Disturb access" screen when needed.
 */
class FocusActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusController.handle(this, intent.getIntExtra(EXTRA_CHOICE, FocusController.TOGGLE))
        finish()
    }

    companion object {
        const val EXTRA_CHOICE = "choice"
    }
}
