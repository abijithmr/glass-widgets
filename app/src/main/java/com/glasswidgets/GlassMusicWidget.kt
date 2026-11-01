package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * Glass music shortcut. With notification access it shows the song, artist and cover; the three
 * buttons always control whatever is playing.
 */
class GlassMusicWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = create(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    companion object {
        fun create(context: Context, preview: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_music)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyTile(context, views, R.id.music_art)
            NowPlaying.bind(
                context, views,
                NowPlaying.Ids(
                    box = R.id.music_art_box, img = R.id.music_art_img, placeholder = R.id.music_art,
                    title = R.id.music_title, sub = R.id.music_sub, playIcon = R.id.music_play_icon,
                    open = R.id.music_open, prev = R.id.music_prev, play = R.id.music_play,
                    next = R.id.music_next
                ),
                requestBase = 60, preview = preview
            )
            return views
        }
    }
}
