package com.glasswidgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import kotlin.math.min

/**
 * What the phone is playing right now, read from Android's media sessions. It needs the one-time
 * "Notification access" permission; without it the music widgets still work as shortcuts.
 */
object NowPlaying {

    class Info(
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val packageName: String?
    )

    /** The view ids a music widget layout provides. Optional ones are 0. */
    class Ids(
        val box: Int, val img: Int, val placeholder: Int,
        val title: Int, val sub: Int, val playIcon: Int, val open: Int,
        val prev: Int, val play: Int, val next: Int,
        val elapsed: Int = 0, val remain: Int = 0, val bar: Int = 0
    )

    private const val APPLE_MUSIC = "com.apple.android.music"

    private fun listener(context: Context) = ComponentName(context, MediaListenerService::class.java)

    fun hasAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = listener(context).flattenToString()
        return flat.split(":").any { it == me }
    }

    fun read(context: Context): Info? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions = try {
            manager.getActiveSessions(listener(context))
        } catch (e: Exception) {
            return null
        }
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull() ?: return null
        val meta = controller.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val state = controller.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        var position = state?.position ?: 0L
        if (playing && state != null) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        position = if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
        return Info(title, artist, art?.let { roundedSquare(it) }, playing, position, duration, controller.packageName)
    }

    fun isPlaying(context: Context): Boolean = read(context)?.playing == true

    private fun roundedSquare(src: Bitmap): Bitmap? = try {
        val size = 160
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val side = min(src.width, src.height)
        val from = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 30f, 30f, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(src, from, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        out
    } catch (e: Exception) {
        null
    }

    fun refreshWidgets(context: Context) {
        Glass.refresh(context, GlassMusicWidget::class.java)
        Glass.refresh(context, GlassMusicCardWidget::class.java)
        Glass.refresh(context, GlassControlCenterWidget::class.java)
    }

    /** Previous / play-pause / next go to whatever is playing, via Android's media keys. */
    fun keyIntent(context: Context, code: Int): android.app.PendingIntent {
        val intent = Intent(context, MediaKeyReceiver::class.java)
            .setAction(MediaKeyReceiver.ACTION_KEY)
            .putExtra(MediaKeyReceiver.EXTRA_KEY, code)
        return android.app.PendingIntent.getBroadcast(
            context, code, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Fill a music layout: art, title, artist, play/pause icon, progress and the buttons. */
    fun bind(context: Context, views: RemoteViews, ids: Ids, requestBase: Int, preview: Boolean) {
        val info = if (preview) null else read(context)
        val access = hasAccess(context)

        val appIntent = (info?.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) })
            ?: context.packageManager.getLaunchIntentForPackage(APPLE_MUSIC)
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)

        when {
            preview -> {
                views.setTextViewText(ids.title, "Song title")
                views.setTextViewText(ids.sub, "Artist")
            }
            info != null -> {
                views.setTextViewText(ids.title, info.title)
                views.setTextViewText(ids.sub, info.artist.ifBlank { if (info.playing) "Playing" else "Paused" })
            }
            access -> {
                views.setTextViewText(ids.title, "Nothing playing")
                views.setTextViewText(ids.sub, "Tap to open music")
            }
            else -> {
                views.setTextViewText(ids.title, "Music")
                views.setTextViewText(ids.sub, "Tap to show what's playing")
            }
        }

        // The art tile always opens the music app; the text row asks for access until it is given.
        views.setOnClickPendingIntent(ids.box, Glass.activity(context, Intent(appIntent), requestBase))
        val openIntent = if (!access && !preview) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent(appIntent)
        }
        views.setOnClickPendingIntent(ids.open, Glass.activity(context, openIntent, requestBase + 1))

        val art = info?.art
        if (art != null) {
            views.setImageViewBitmap(ids.img, art)
            views.setViewVisibility(ids.img, View.VISIBLE)
            views.setViewVisibility(ids.placeholder, View.GONE)
        } else {
            views.setViewVisibility(ids.img, View.GONE)
            views.setViewVisibility(ids.placeholder, View.VISIBLE)
        }

        views.setImageViewResource(
            ids.playIcon, if (info?.playing == true) R.drawable.ic_pause else R.drawable.ic_play
        )

        if (ids.bar != 0) {
            val position = if (preview) 42_000L else info?.positionMs ?: 0L
            val duration = if (preview) 180_000L else info?.durationMs ?: 0L
            val progress = if (duration > 0) (1000L * position / duration).toInt() else 0
            views.setProgressBar(ids.bar, 1000, progress.coerceIn(0, 1000), false)
        }
        if (ids.elapsed != 0 && ids.remain != 0) {
            val now = SystemClock.elapsedRealtime()
            val position = if (preview) 42_000L else info?.positionMs ?: 0L
            val duration = if (preview) 180_000L else info?.durationMs ?: 0L
            val running = info?.playing == true
            views.setChronometer(ids.elapsed, now - position, null, running)
            if (duration > 0) {
                views.setViewVisibility(ids.remain, View.VISIBLE)
                views.setChronometerCountDown(ids.remain, true)
                views.setChronometer(ids.remain, now + (duration - position), "-%s", running)
            } else {
                views.setViewVisibility(ids.remain, View.INVISIBLE)
            }
        }

        views.setOnClickPendingIntent(ids.prev, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        views.setOnClickPendingIntent(ids.play, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        views.setOnClickPendingIntent(ids.next, keyIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
    }
}
