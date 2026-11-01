package com.glasswidgets

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
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

    /** Each core's current clock as a share of its own maximum; null where Android blocks it. */
    fun coreFractions(): List<Float?> = (0 until Runtime.getRuntime().availableProcessors()).map { i ->
        val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
        val cur = readFirstLine(base + "scaling_cur_freq")?.trim()?.toLongOrNull()
        val max = readFirstLine(base + "cpuinfo_max_freq")?.trim()?.toLongOrNull()
        if (cur != null && max != null && max > 0) (100f * cur / max) else null
    }

    fun socName(): String =
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != Build.UNKNOWN) {
            Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }

    /** "up 3d 4h" / "up 5h 12m", from the time since the phone started. */
    fun uptime(): String {
        val minutes = android.os.SystemClock.elapsedRealtime() / 60_000
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        return if (days > 0) "up ${days}d ${hours}h" else "up ${hours}h ${minutes % 60}m"
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

    class Zone(val dir: String, val type: String, val raw: String?, val celsius: Float?)

    /** Every kernel thermal zone, readable or not. */
    fun zones(): List<Zone> {
        val dirs = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?: return emptyList()
        return dirs.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }.map { dir ->
            val type = readFirstLine(File(dir, "type").path)?.trim() ?: "?"
            val raw = readFirstLine(File(dir, "temp").path)?.trim()
            val value = raw?.toFloatOrNull()
            val celsius = value?.let { if (abs(it) >= 1000f) it / 1000f else it }
                ?.takeIf { it in 5f..125f }
            Zone(dir.name, type, raw, celsius)
        }
    }

    private fun isCpuLike(type: String): Boolean {
        val t = type.lowercase()
        return t.contains("cpu") || t.contains("soc") || t.contains("tsens") ||
            t.contains("big") || t.contains("little") || t.contains("mid") || t.contains("core")
    }

    const val SRC_AUTO = "auto"
    const val SRC_AVG = "avg"
    const val SRC_BATTERY = "battery"
    const val ZONE_PREFIX = "zone:"
    private const val KEY_TEMP_SOURCE = "monitor_temp_source"
    private const val KEY_STORAGE_SOURCE = "monitor_storage_source"

    fun tempSource(context: Context): String = Glass.prefs(context).getString(KEY_TEMP_SOURCE, SRC_AUTO) ?: SRC_AUTO
    fun setTempSource(context: Context, value: String) =
        Glass.prefs(context).edit().putString(KEY_TEMP_SOURCE, value).apply()

    /** Returns the temperature and a short name of the sensor it came from. */
    private fun cpuTemperature(context: Context): Pair<Float, String>? {
        val readable = zones().filter { it.celsius != null }
        val source = tempSource(context)
        if (source == SRC_BATTERY) {
            val tenths = batteryIntent(context)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (tenths != null && tenths != Int.MIN_VALUE) return tenths / 10f to "battery sensor"
        }
        if (source.startsWith(ZONE_PREFIX)) {
            val name = source.removePrefix(ZONE_PREFIX)
            readable.firstOrNull { it.type == name }?.let { return it.celsius!! to it.type }
        }
        val cpuZones = readable.filter { isCpuLike(it.type) }
        if (cpuZones.isEmpty()) return null
        return if (source == SRC_AVG) {
            cpuZones.map { it.celsius!! }.average().toFloat() to "average of ${cpuZones.size} CPU sensors"
        } else {
            val hottest = cpuZones.maxByOrNull { it.celsius!! }!!
            hottest.celsius!! to "hottest CPU sensor"
        }
    }

    fun cpuTemp(context: Context): Cell {
        val state = thermalState(context)
        val found = cpuTemperature(context)
        return if (found != null) {
            val (temp, name) = found
            Cell(
                String.format(Locale.US, "%.1f°C", temp),
                if (tempSource(context) == SRC_AUTO) "Thermal: ${state ?: "n/a"}" else name,
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

    const val STORAGE_SETTINGS = "settings"
    const val STORAGE_PARTITION_FREE = "partition_free"
    const val STORAGE_DATA = "data"

    fun storageSource(context: Context): String =
        Glass.prefs(context).getString(KEY_STORAGE_SOURCE, STORAGE_SETTINGS) ?: STORAGE_SETTINGS

    fun setStorageSource(context: Context, value: String) =
        Glass.prefs(context).edit().putString(KEY_STORAGE_SOURCE, value).apply()

    /** The phone's real capacity (e.g. 256 GB), if Android reports it. */
    private fun capacity(context: Context): Long? = try {
        context.getSystemService(StorageStatsManager::class.java).getTotalBytes(StorageManager.UUID_DEFAULT)
    } catch (e: Exception) {
        null
    }

    fun storage(context: Context): Cell {
        val source = storageSource(context)
        var total: Long? = null
        var used: Long? = null
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val cap = capacity(context)
            when (source) {
                STORAGE_DATA -> {
                    total = stat.totalBytes
                    used = stat.totalBytes - stat.availableBytes
                }
                STORAGE_PARTITION_FREE -> if (cap != null) {
                    total = cap
                    used = cap - stat.availableBytes
                }
                else -> if (cap != null) {
                    // Same as Android Settings: capacity minus free space (free counts clearable cache)
                    val free = context.getSystemService(StorageStatsManager::class.java)
                        .getFreeBytes(StorageManager.UUID_DEFAULT)
                    total = cap
                    used = cap - free
                }
            }
            if (total == null || used == null) {
                total = stat.totalBytes
                used = stat.totalBytes - stat.availableBytes
            }
        } catch (e: Exception) {
            return Cell("N/A", "No reading")
        }
        val usedBytes = used.coerceAtLeast(0L)
        val percent = (100.0 * usedBytes / total).toInt()
        return Cell("${gbDecimal(usedBytes)} / ${gbDecimal(total)} GB", "$percent% used", percent.toFloat())
    }

    private fun gbDecimal(bytes: Long): String = String.format(Locale.US, "%.0f", bytes / 1_000_000_000.0)

    // ------------------------------------------------------------------ report

    /** Every raw number the widget could use, for checking against other apps. */
    fun report(context: Context): String {
        val sb = StringBuilder()
        fun line(text: String) { sb.append(text).append('\n') }
        line("THERMAL ZONES (kernel)")
        val zs = zones()
        if (zs.isEmpty()) line("  none readable: Android blocks /sys/class/thermal")
        for (z in zs) {
            line("  %-14s %-24s %s".format(z.dir, z.type, if (z.raw == null) "blocked" else z.celsius?.let { String.format(Locale.US, "%.1f°C (raw %s)", it, z.raw) } ?: "raw ${z.raw}"))
        }
        line("")
        line("BATTERY")
        val b = batteryIntent(context)
        val manager = context.getSystemService(BatteryManager::class.java)
        line("  temperature  " + (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.let { it / 10f } ?: "?") + "°C")
        line("  voltage      " + (b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: "?") + " mV")
        line("  current raw  " + (manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: "?"))
        line("  level        " + (manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: "?") + "%")
        line("")
        line("STORAGE (bytes)")
        try {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            line("  capacity (StorageStats)   $total  = ${gbDecimal(total)} GB")
            line("  free incl. cache          $free  = ${gbDecimal(free)} GB")
            line("  used = capacity - free    ${total - free}  = ${gbDecimal(total - free)} GB")
        } catch (e: Exception) {
            line("  StorageStats failed: ${e.javaClass.simpleName}")
        }
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            line("  /data total               ${st.totalBytes}  = ${gbDecimal(st.totalBytes)} GB")
            line("  /data available           ${st.availableBytes}  = ${gbDecimal(st.availableBytes)} GB")
            line("  /data used                ${st.totalBytes - st.availableBytes}  = ${gbDecimal(st.totalBytes - st.availableBytes)} GB")
        } catch (e: Exception) {
            line("  StatFs failed: ${e.javaClass.simpleName}")
        }
        line("")
        line("MEMORY")
        context.getSystemService(ActivityManager::class.java)?.let {
            val info = ActivityManager.MemoryInfo()
            it.getMemoryInfo(info)
            line("  total      ${info.totalMem}")
            line("  available  ${info.availMem}")
        }
        line("")
        line("CPU")
        line("  cores  ${Runtime.getRuntime().availableProcessors()}   SoC ${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE}")
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readFirstLine(base + "scaling_cur_freq")?.trim()
            val max = readFirstLine(base + "cpuinfo_max_freq")?.trim()
            line("  cpu$i  cur ${cur ?: "blocked"} kHz   max ${max ?: "blocked"} kHz")
        }
        line("  /proc/stat  " + if (readFirstLine("/proc/stat") != null) "readable" else "blocked by Android")
        return sb.toString()
    }
}
