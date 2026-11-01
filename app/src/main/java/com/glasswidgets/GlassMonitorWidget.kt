package com.glasswidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The three System Monitor looks, picked in the app. */
object MonitorLook {
    const val GAUGES = 0
    const val TERMINAL = 1
    const val THERMAL = 2

    val NAMES = listOf("Gauges", "Terminal", "Thermal dashboard")
    private const val KEY = "monitor_look"

    fun get(context: Context): Int = Glass.prefs(context).getInt(KEY, GAUGES).coerceIn(0, 2)

    fun set(context: Context, look: Int) {
        Glass.prefs(context).edit().putInt(KEY, look).apply()
        Glass.refresh(context.applicationContext, GlassMonitorWidget::class.java)
    }

    /** Dialog that lets the person choose a look or open the sensor screen. [context] must be an activity. */
    fun pick(context: Context, onChosen: () -> Unit) {
        val current = get(context)
        val items = NAMES.mapIndexed { i, name -> (if (i == current) "●  " else "○  ") + name } +
            "Sensors & sources…"
        MaterialAlertDialogBuilder(context)
            .setTitle("System Monitor")
            .setItems(items.toTypedArray()) { dialog, which ->
                if (which < NAMES.size) {
                    set(context, which)
                    onChosen()
                } else {
                    context.startActivity(android.content.Intent(context, SensorsActivity::class.java))
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

/**
 * System monitor: CPU, temperatures, battery, memory and storage read live from the phone.
 * While the screen is on it redraws every 5 seconds; with the screen off it does nothing at all.
 */
class GlassMonitorWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = create(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
        startLive(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                push(context)
                startLive(context)
            }
            ACTION_TICK -> tick(context)
            ACTION_KICK -> {
                // Restarts the 5-second loop after the screen was off
                val stale = SystemClock.elapsedRealtime() - lastTick(context) > 12_000
                if (stale && isInteractive(context)) tick(context)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms?.cancel(broadcast(context, ACTION_TICK, 71))
        alarms?.cancel(broadcast(context, ACTION_KICK, 73))
    }

    companion object {
        private const val ACTION_REFRESH = "com.glasswidgets.MONITOR_REFRESH"
        private const val ACTION_TICK = "com.glasswidgets.MONITOR_TICK"
        private const val ACTION_KICK = "com.glasswidgets.MONITOR_KICK"
        private const val LIVE_MS = 5_000L
        private const val KICK_MS = 60_000L
        private const val KEY_LAST_TICK = "monitor_last_tick"

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, GlassMonitorWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun isInteractive(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)?.isInteractive ?: false

        private fun lastTick(context: Context): Long = Glass.prefs(context).getLong(KEY_LAST_TICK, 0L)

        private fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlassMonitorWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, create(context))
        }

        /** One live step: redraw, then ask for the next one only if the screen is on. */
        private fun tick(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.getAppWidgetIds(ComponentName(context, GlassMonitorWidget::class.java)).isEmpty()) return
            push(context)
            Glass.prefs(context).edit().putLong(KEY_LAST_TICK, SystemClock.elapsedRealtime()).apply()
            if (isInteractive(context)) scheduleNext(context)
        }

        private fun scheduleNext(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = SystemClock.elapsedRealtime() + LIVE_MS
            val pi = broadcast(context, ACTION_TICK, 71)
            try {
                if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                    alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi)
                } else {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi)
                }
            } catch (e: SecurityException) {
                alarms.set(AlarmManager.ELAPSED_REALTIME, at, pi)
            }
        }

        /** Starts the loop, and a once-a-minute nudge that revives it after the screen was off. */
        private fun startLive(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + KICK_MS,
                KICK_MS,
                broadcast(context, ACTION_KICK, 73)
            )
            if (isInteractive(context)) scheduleNext(context)
        }

        // ------------------------------------------------------------ drawing

        private const val CPU = 0xFF5CC8FF.toInt()
        private const val CPU_TEMP = 0xFFFF8A5B.toInt()
        private const val BATT_TEMP = 0xFFFFC857.toInt()
        private const val BATT = 0xFF6FD38B.toInt()
        private const val RAM = 0xFFB48CFF.toInt()
        private const val STOR = 0xFF4DD0C8.toInt()

        private fun percent(cell: SystemStats.Cell): String =
            cell.fraction?.toInt()?.let { "$it%" } ?: "N/A"

        fun create(context: Context): RemoteViews {
            val cpu = SystemStats.cpu(context)
            val cpuTemp = SystemStats.cpuTemp(context)
            val battTemp = SystemStats.batteryTemp(context)
            val batt = SystemStats.battery(context)
            val ram = SystemStats.memory(context)
            val stor = SystemStats.storage(context)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val views = when (MonitorLook.get(context)) {
                MonitorLook.TERMINAL -> terminal(context, cpu, cpuTemp, battTemp, batt, ram, stor, time)
                MonitorLook.THERMAL -> thermal(context, cpu, cpuTemp, battTemp, batt, ram, stor, time)
                else -> gauges(context, cpu, cpuTemp, battTemp, batt, ram, stor, time)
            }
            views.setOnClickPendingIntent(R.id.widget_root, broadcast(context, ACTION_REFRESH, 72))
            return views
        }

        private fun gauges(
            context: Context, cpu: SystemStats.Cell, cpuTemp: SystemStats.Cell,
            battTemp: SystemStats.Cell, batt: SystemStats.Cell, ram: SystemStats.Cell,
            stor: SystemStats.Cell, time: String
        ): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_glass_monitor_gauges)
            Glass.applyCard(context, v, R.id.widget_root)
            v.setTextViewText(R.id.mg_updated, time)

            val (cpuValue, cpuUnit) = when {
                cpu.value.endsWith(" GHz") -> cpu.value.removeSuffix(" GHz") to "GHz"
                cpu.value.endsWith("%") -> cpu.value to "load"
                else -> cpu.value to ""
            }
            v.setTextViewText(R.id.mg_cpu_val, cpuValue)
            v.setTextViewText(R.id.mg_cpu_unit, cpuUnit)
            v.setTextViewText(R.id.mg_cpu_sub, cpu.sub)
            v.setTextViewText(R.id.mg_ram_val, percent(ram))
            v.setTextViewText(R.id.mg_ram_unit, "in use")
            v.setTextViewText(R.id.mg_ram_sub, ram.value)
            v.setTextViewText(R.id.mg_stor_val, percent(stor))
            v.setTextViewText(R.id.mg_stor_unit, "used")
            v.setTextViewText(R.id.mg_stor_sub, stor.value)

            v.setImageViewBitmap(R.id.mg_ring_cpu, MonitorDraw.ring(CPU, cpu.fraction))
            v.setImageViewBitmap(R.id.mg_ring_ram, MonitorDraw.ring(RAM, ram.fraction))
            v.setImageViewBitmap(R.id.mg_ring_stor, MonitorDraw.ring(STOR, stor.fraction))

            v.setTextViewText(R.id.mg_ct_val, cpuTemp.value)
            v.setTextViewText(R.id.mg_ct_sub, cpuTemp.sub)
            v.setTextViewText(R.id.mg_bt_val, battTemp.value)
            v.setTextViewText(R.id.mg_bt_sub, battTemp.sub)
            v.setTextViewText(R.id.mg_ba_val, batt.value)
            v.setTextViewText(R.id.mg_ba_sub, batt.sub)
            return v
        }

        private fun terminal(
            context: Context, cpu: SystemStats.Cell, cpuTemp: SystemStats.Cell,
            battTemp: SystemStats.Cell, batt: SystemStats.Cell, ram: SystemStats.Cell,
            stor: SystemStats.Cell, time: String
        ): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_glass_monitor_terminal)
            v.setTextViewText(R.id.mt_updated, time)

            fun row(bar: Int, value: Int, color: Int, cell: SystemStats.Cell, text: String) {
                v.setImageViewBitmap(bar, MonitorDraw.segments(color, cell.fraction))
                v.setTextViewText(value, text)
            }
            row(R.id.mt_bar_cpu, R.id.mt_v_cpu, CPU, cpu, cpu.value)
            row(R.id.mt_bar_temp, R.id.mt_v_temp, CPU_TEMP, cpuTemp, cpuTemp.value)
            row(R.id.mt_bar_mem, R.id.mt_v_mem, RAM, ram, percent(ram))
            row(R.id.mt_bar_disk, R.id.mt_v_disk, STOR, stor, percent(stor))
            row(R.id.mt_bar_bat, R.id.mt_v_bat, BATT, batt, batt.value)
            row(R.id.mt_bar_btmp, R.id.mt_v_btmp, BATT_TEMP, battTemp, battTemp.value)

            v.setTextViewText(R.id.mt_f1, "${cpu.sub} · ${cpuTemp.sub}".lowercase(Locale.US))
            v.setTextViewText(R.id.mt_f2, "mem ${ram.value} · disk ${stor.value}")
            v.setTextViewText(R.id.mt_f3, "batt ${batt.sub} · ${battTemp.sub}".lowercase(Locale.US))
            return v
        }

        private fun thermal(
            context: Context, cpu: SystemStats.Cell, cpuTemp: SystemStats.Cell,
            battTemp: SystemStats.Cell, batt: SystemStats.Cell, ram: SystemStats.Cell,
            stor: SystemStats.Cell, time: String
        ): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_glass_monitor_thermal)
            Glass.applyCard(context, v, R.id.widget_root)
            v.setTextViewText(R.id.md_updated, time)

            fun big(bigId: Int, smallId: Int, value: String) {
                val dot = value.indexOf('.')
                if (dot > 0 && value.first().isDigit()) {
                    v.setTextViewTextSize(bigId, TypedValue.COMPLEX_UNIT_SP, 40f)
                    v.setTextViewText(bigId, value.substring(0, dot))
                    v.setTextViewText(smallId, value.substring(dot))
                } else {
                    // No sensor reading (a word such as "Normal"): show it smaller
                    v.setTextViewTextSize(bigId, TypedValue.COMPLEX_UNIT_SP, 26f)
                    v.setTextViewText(bigId, value)
                    v.setTextViewText(smallId, "")
                }
            }
            big(R.id.md_cpu_big, R.id.md_cpu_small, cpuTemp.value)
            big(R.id.md_bt_big, R.id.md_bt_small, battTemp.value)
            v.setTextViewText(R.id.md_cpu_sub, "${cpuTemp.sub} · ${cpu.value}")
            v.setTextViewText(R.id.md_bt_sub, "${battTemp.sub} · ${batt.sub.substringBefore(" · ")}")
            v.setImageViewBitmap(R.id.md_cpu_bar, MonitorDraw.bar(BATT_TEMP, CPU_TEMP, cpuTemp.fraction))
            v.setImageViewBitmap(R.id.md_bt_bar, MonitorDraw.bar(BATT, BATT_TEMP, battTemp.fraction))

            v.setImageViewBitmap(R.id.md_ram_bar, MonitorDraw.bar(RAM, RAM, ram.fraction))
            v.setTextViewText(R.id.md_ram_val, ram.value)
            v.setImageViewBitmap(R.id.md_stor_bar, MonitorDraw.bar(STOR, STOR, stor.fraction))
            v.setTextViewText(R.id.md_stor_val, stor.value)
            v.setImageViewBitmap(R.id.md_chg_bar, MonitorDraw.bar(BATT, BATT, batt.fraction))
            val volts = batt.sub.substringAfter(" · ", "")
            v.setTextViewText(R.id.md_chg_val, if (volts.isEmpty()) batt.value else "${batt.value} · $volts")
            return v
        }
    }
}
