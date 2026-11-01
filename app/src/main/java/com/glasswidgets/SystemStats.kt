package com.glasswidgets

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Real readings straight from Android and the kernel. Nothing here is estimated: when Android
 * does not let an app read something, the value comes back as null and the widget says so.
 */
object SystemStats {

    /** [fraction] is the reading on a 0-100 scale for the graph, or null when there is none. */
    class Cell(val value: String, val sub: String, val fraction: Float? = null)

    private const val PREFS = "monitor_prefs"
    private const val KEY_TOTAL = "stat_total"
    private const val KEY_IDLE = "stat_idle"

    private fun readFirstLine(path: String): String? = try {
        File(path).bufferedReader().use { it.readLine() }
    } catch (e: Exception) {
        null
    }

    private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1073741824.0)

    // ------------------------------------------------------------------ CPU

    /** Busy share of all cores since the previous reading, from /proc/stat (blocked on many phones). */
    private fun cpuLoad(context: Context): Int? {
        val parts = readFirstLine("/proc/stat")?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 8 || parts[0] != "cpu") return null
        val nums = try {
            parts.subList(1, 9.coerceAtMost(parts.size)).map { it.toLong() }
        } catch (e: NumberFormatException) {
            return null
        }
        val total = nums.sum()
        val idle = nums[3] + nums[4]
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTotal = prefs.getLong(KEY_TOTAL, -1L)
        val lastIdle = prefs.getLong(KEY_IDLE, -1L)
        prefs.edit().putLong(KEY_TOTAL, total).putLong(KEY_IDLE, idle).apply()
        val dTotal = total - lastTotal
        if (lastTotal < 0 || dTotal <= 0) return null
        return (100.0 * (dTotal - (idle - lastIdle)) / dTotal).toInt().coerceIn(0, 100)
    }

    /** Fastest core's current clock in GHz, and the matching maximum, if the kernel lets us read it. */
    private fun cpuClock(): Pair<Double, Double?>? {
        var best = -1L
        var bestMax: Long? = null
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readFirstLine(base + "scaling_cur_freq")?.trim()?.toLongOrNull() ?: continue
            if (cur > best) {
                best = cur
                bestMax = readFirstLine(base + "cpuinfo_max_freq")?.trim()?.toLongOrNull()
            }
        }
        if (best < 0) return null
        return best / 1_000_000.0 to bestMax?.let { it / 1_000_000.0 }
    }

    fun cpu(context: Context): Cell {
        val cores = Runtime.getRuntime().availableProcessors()
        val soc = if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.isNotBlank() &&
            Build.SOC_MODEL != Build.UNKNOWN
        ) Build.SOC_MODEL else Build.HARDWARE
        val load = cpuLoad(context)
        val clock = cpuClock()
        val clockText = clock?.let { String.format(Locale.US, "%.2f GHz", it.first) }
        val value = when {
            load != null -> "$load%"
            clockText != null -> clockText
            else -> "N/A"
        }
        val sub = buildString {
            if (load != null && clockText != null) append(clockText).append(" · ")
            append("$cores cores")
            if (load == null && clockText == null) append(" · usage blocked by Android")
            else if (soc.isNotBlank()) append(" · ").append(soc)
        }
        val fraction = when {
            load != null -> load.toFloat()
            clock?.second != null && clock.second!! > 0 -> (100f * clock.first / clock.second!!).toFloat()
            else -> null
        }
        return Cell(value, sub, fraction)
    }

    // --------------------------------------------------------------- thermals

    private fun thermalState(context: Context): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val pm = context.getSystemService(PowerManager::class.java) ?: return null
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> null
        }
    }

    /** Hottest CPU/SoC thermal zone the kernel lets an app read, in °C. */
    private fun cpuTemperature(): Float? {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?: return null
        var best: Float? = null
        for (zone in zones) {
            val type = readFirstLine(File(zone, "type").path)?.lowercase() ?: continue
            if (!(type.contains("cpu") || type.contains("soc") || type.contains("tsens") ||
                    type.contains("big") || type.contains("little") || type.contains("mid"))
            ) continue
            val raw = readFirstLine(File(zone, "temp").path)?.trim()?.toFloatOrNull() ?: continue
            val celsius = if (abs(raw) >= 1000f) raw / 1000f else raw
            if (celsius in 10f..125f && (best == null || celsius > best)) best = celsius
        }
        return best
    }

    fun cpuTemp(context: Context): Cell {
        val state = thermalState(context)
        val temp = cpuTemperature()
        return if (temp != null) {
            Cell(
                String.format(Locale.US, "%.1f°C", temp), "Thermal: ${state ?: "n/a"}",
                ((temp - 20f) / 60f * 100f).coerceIn(0f, 100f)
            )
        } else if (state != null) {
            Cell(state, "Thermal state · no sensor access")
        } else {
            Cell("N/A", "Not exposed by Android")
        }
    }

    // ---------------------------------------------------------------- battery

    private fun batteryIntent(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryTemp(context: Context): Cell {
        val intent = batteryIntent(context) ?: return Cell("N/A", "No reading")
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return Cell("N/A", "No sensor")
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Health good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Health unknown"
        }
        return Cell(
            String.format(Locale.US, "%.1f°C", tenths / 10f), health,
            ((tenths / 10f - 15f) / 35f * 100f).coerceIn(0f, 100f)
        )
    }

    fun battery(context: Context): Cell {
        val intent = batteryIntent(context)
        val manager = context.getSystemService(BatteryManager::class.java)
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = manager?.isCharging ?: false
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val raw = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        // Phones disagree on the unit: most report microamps, a few milliamps
        val magnitude = abs(raw)
        val milliamps = if (magnitude >= 10_000) magnitude / 1000 else magnitude

        val parts = ArrayList<String>()
        if (milliamps > 0 && raw != Long.MIN_VALUE) {
            parts.add("${if (charging) "+" else "−"}$milliamps mA")
        }
        if (mv > 0) parts.add(String.format(Locale.US, "%.2f V", mv / 1000f))
        if (parts.isEmpty()) parts.add(if (charging) "Charging" else "On battery")
        return Cell(
            if (level in 0..100) "$level%" else "N/A", parts.joinToString(" · "),
            if (level in 0..100) level.toFloat() else null
        )
    }

    // ----------------------------------------------------------- memory, disk

    fun memory(context: Context): Cell {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return Cell("N/A", "No reading")
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        val percent = (100.0 * used / info.totalMem).toInt()
        return Cell("${gb(used)} / ${gb(info.totalMem)} GB", "$percent% in use", percent.toFloat())
    }

    fun storage(): Cell = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val used = total - stat.availableBytes
        val percent = (100.0 * used / total).toInt()
        Cell("${gb(used)} / ${gb(total)} GB", "$percent% used", percent.toFloat())
    } catch (e: Exception) {
        Cell("N/A", "No reading")
    }
}
