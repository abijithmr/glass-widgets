package com.glasswidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Larger glass music card: cover, song, artist, elapsed and remaining time, progress and controls. */
class GlassMusicCardWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_glass_music_card)
            Glass.applyCard(context, views, R.id.widget_root)
            Glass.applyTile(context, views, R.id.mc_art)
            NowPlaying.bind(
                context, views,
                NowPlaying.Ids(
                    box = R.id.mc_art_box, img = R.id.mc_art_img, placeholder = R.id.mc_art,
                    title = R.id.mc_title, sub = R.id.mc_sub, playIcon = R.id.mc_play_icon,
                    open = R.id.mc_open, prev = R.id.mc_prev, play = R.id.mc_play,
                    next = R.id.mc_next, elapsed = R.id.mc_elapsed, remain = R.id.mc_remain,
                    bar = R.id.mc_bar
                ),
                requestBase = 90, preview = preview
            )
            return views
        }
    }
}
