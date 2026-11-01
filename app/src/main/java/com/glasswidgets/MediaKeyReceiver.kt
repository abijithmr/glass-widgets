package com.glasswidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** Sends a media key (previous, play/pause, next) to whatever app is playing. */
class MediaKeyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY) return
        val code = intent.getIntExtra(EXTRA_KEY, 0)
        if (code == 0) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        const val ACTION_KEY = "com.glasswidgets.ACTION_MEDIA_KEY"
        const val EXTRA_KEY = "key"
    }
}
