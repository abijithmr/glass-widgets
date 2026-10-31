package com.glasswidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.RemoteViews

/**
 * A glass music shortcut. The title area opens Apple Music (or your default music app);
 * the three buttons send previous / play-pause / next to whatever is playing.
 */
class GlassMusicWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, create(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_KEY) {
            val code = intent.getIntExtra(EXTRA_KEY, 0)
            if (code != 0) sendKey(context, code)
        }
    }

    private fun sendKey(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        private const val ACTION_KEY = "com.glasswidgets.ACTION_MEDIA_KEY"
        private const val EXTRA_KEY = "key"
        private const val APPLE_MUSIC = "com.apple.android.music"

        private fun keyIntent(context: Context, code: Int): PendingIntent {
            val intent = Intent(context, GlassMusicWidget::class.java)
                .setAction(ACTION_KEY)
                .putExtra(EXTRA_KEY, code)
            return PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun create(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_music)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyTile(context, views, R.id.music_art)

            val apple = context.packageManager.getLaunchIntentForPackage(APPLE_MUSIC)
            val open = apple ?: Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC
            )
            views.setTextViewText(R.id.music_title, if (apple != null) "Apple Music" else "Music")
            views.setOnClickPendingIntent(R.id.music_open, Glass.activity(context, open, 61))

            views.setOnClickPendingIntent(
                R.id.music_prev, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            )
            views.setOnClickPendingIntent(
                R.id.music_play, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.music_next, keyIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            )
            return views
        }
    }
}
