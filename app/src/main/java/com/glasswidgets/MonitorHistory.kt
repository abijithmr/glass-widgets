package com.glasswidgets

import android.content.Context

/**
 * A small log of real CPU temperature readings for the Horizon and Heat log looks.
 * Readings are only taken when the widget redraws (live loop, screen on), so it costs no extra battery;
 * time with the screen off simply stays empty and is never estimated.
 */
object MonitorHistory {
    private const val KEY_FINE = "mh_fine"
    private const val KEY_COARSE = "mh_coarse"

    const val FINE_SPAN_MS = 5 * 60_000L
    private const val FINE_GAP_MS = 4_000L
    private const val FINE_MAX = 80
    const val BUCKET_MS = 5 * 60_000L
    const val BUCKETS = 36

    /** Adds one reading, [celsius], taken at [now]. */
    fun record(context: Context, celsius: Float, now: Long = System.currentTimeMillis()) {
        val prefs = Glass.prefs(context)

        val fine = parseFine(prefs.getString(KEY_FINE, null)).toMutableList()
        if (fine.isEmpty() || now - fine.last().first >= FINE_GAP_MS) fine.add(now to celsius)
        val keepFrom = now - FINE_SPAN_MS
        val trimmed = fine.filter { it.first >= keepFrom }.takeLast(FINE_MAX)

        val coarse = parseCoarse(prefs.getString(KEY_COARSE, null)).toMutableMap()
        val bucket = now / BUCKET_MS
        val previous = coarse[bucket]
        coarse[bucket] = if (previous == null || celsius > previous) celsius else previous
        coarse.keys.removeAll { it <= bucket - BUCKETS }

        prefs.edit()
            .putString(KEY_FINE, trimmed.joinToString(",") { "${it.first}:${"%.1f".format(java.util.Locale.US, it.second)}" })
            .putString(KEY_COARSE, coarse.entries.joinToString(",") { "${it.key}:${"%.1f".format(java.util.Locale.US, it.value)}" })
            .apply()
    }

    /** Readings of the last five minutes as (time in ms, °C), oldest first. */
    fun fine(context: Context, now: Long = System.currentTimeMillis()): List<Pair<Long, Float>> =
        parseFine(Glass.prefs(context).getString(KEY_FINE, null)).filter { it.first >= now - FINE_SPAN_MS }

    /** The hottest reading in each 5-minute block for the last 3 hours, oldest first; null where nothing was recorded. */
    fun coarse(context: Context, now: Long = System.currentTimeMillis()): List<Float?> {
        val map = parseCoarse(Glass.prefs(context).getString(KEY_COARSE, null))
        val bucket = now / BUCKET_MS
        return (bucket - BUCKETS + 1..bucket).map { map[it] }
    }

    private fun parseFine(raw: String?): List<Pair<Long, Float>> =
        raw.orEmpty().split(',').mapNotNull {
            val p = it.split(':')
            val t = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toFloatOrNull()
            if (t != null && v != null) t to v else null
        }

    private fun parseCoarse(raw: String?): Map<Long, Float> =
        raw.orEmpty().split(',').mapNotNull {
            val p = it.split(':')
            val b = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toFloatOrNull()
            if (b != null && v != null) b to v else null
        }.toMap()
}
