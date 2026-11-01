package com.glasswidgets

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService

/**
 * Lets the music widgets know when the song or play state changes. It reads no notifications:
 * it exists because Android only shows media sessions to apps with notification access.
 * Idle cost is zero; while music plays with the screen on it redraws the widgets every 10 s.
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val controllers = ArrayList<MediaController>()
    private var manager: MediaSessionManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = scheduleRefresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRefresh()
        override fun onSessionDestroyed() = scheduleRefresh()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            attach(list)
            scheduleRefresh()
        }

    private val refresh = Runnable {
        NowPlaying.refreshWidgets(applicationContext)
        scheduleTick()
    }

    private val tick = Runnable {
        if (isPlayingWithScreenOn()) {
            NowPlaying.refreshWidgets(applicationContext)
            scheduleTick()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            manager = msm
            val me = ComponentName(this, MediaListenerService::class.java)
            msm.addOnActiveSessionsChangedListener(sessionsListener, me)
            attach(msm.getActiveSessions(me))
        } catch (e: Exception) {
            android.util.Log.e("GlassWidgets", "Media listener failed", e)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = scheduleRefresh()
        }.also { registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_ON)) }
        scheduleRefresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanUp()
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    private fun cleanUp() {
        handler.removeCallbacksAndMessages(null)
        detach()
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        screenReceiver = null
    }

    private fun attach(list: List<MediaController>?) {
        detach()
        list?.forEach {
            it.registerCallback(callback, handler)
            controllers.add(it)
        }
    }

    private fun detach() {
        controllers.forEach {
            try {
                it.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        controllers.clear()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 300)
    }

    private fun isPlayingWithScreenOn(): Boolean {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: false
        return interactive && controllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (isPlayingWithScreenOn()) handler.postDelayed(tick, 10_000)
    }
}
