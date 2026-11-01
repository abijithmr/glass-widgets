package com.glasswidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * System monitor: CPU, CPU temperature, battery temperature and charge, memory and storage,
 * read live from the phone. It redraws on tap and every two minutes while the phone is awake
 * (the timer never wakes a sleeping phone).
 */
class GlassMonitorWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = create(context, record = true)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
        schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlassMonitorWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, create(context, record = true))
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    companion object {
        private const val ACTION_REFRESH = "com.glasswidgets.MONITOR_REFRESH"
        private const val ACTION_TICK = "com.glasswidgets.MONITOR_TICK"
        private const val INTERVAL_MS = 2 * 60 * 1000L

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, GlassMonitorWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun tickIntent(context: Context) = broadcast(context, ACTION_TICK, 71)

        /** Non-waking repeating timer: it only fires while the phone is already awake. */
        private fun schedule(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                tickIntent(context)
            )
        }

        private class Metric(
            val key: String, val color: Int, val bar: Boolean,
            val valueId: Int, val subId: Int, val graphId: Int, val cell: SystemStats.Cell
        )

        /** [record] is true only for real widget updates, so in-app previews never add history. */
        fun create(context: Context, record: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_monitor)
            Glass.applyCard(context, views, R.id.widget_root)

            val metrics = listOf(
                Metric("cpu", 0xFF5CC8FF.toInt(), false, R.id.mon_cpu, R.id.mon_cpu_sub, R.id.mon_cpu_g, SystemStats.cpu(context)),
                Metric("cputemp", 0xFFFF8A5B.toInt(), false, R.id.mon_cputemp, R.id.mon_cputemp_sub, R.id.mon_cputemp_g, SystemStats.cpuTemp(context)),
                Metric("btemp", 0xFFFFC857.toInt(), false, R.id.mon_btemp, R.id.mon_btemp_sub, R.id.mon_btemp_g, SystemStats.batteryTemp(context)),
                Metric("batt", 0xFF6FD38B.toInt(), true, R.id.mon_batt, R.id.mon_batt_sub, R.id.mon_batt_g, SystemStats.battery(context)),
                Metric("ram", 0xFFB48CFF.toInt(), true, R.id.mon_ram, R.id.mon_ram_sub, R.id.mon_ram_g, SystemStats.memory(context)),
                Metric("stor", 0xFF4DD0C8.toInt(), true, R.id.mon_stor, R.id.mon_stor_sub, R.id.mon_stor_g, SystemStats.storage())
            )
            for (m in metrics) {
                if (record) MonitorGraph.record(context, m.key, m.cell.fraction)
                views.setTextViewText(m.valueId, m.cell.value)
                views.setTextViewText(m.subId, m.cell.sub)
                views.setImageViewBitmap(
                    m.graphId,
                    MonitorGraph.draw(m.color, MonitorGraph.history(context, m.key), m.cell.fraction, m.bar)
                )
            }
            views.setTextViewText(
                R.id.mon_updated,
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            )
            views.setOnClickPendingIntent(R.id.widget_root, broadcast(context, ACTION_REFRESH, 72))
            return views
        }
    }
}
