package com.glasswidgets

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Weather from Open-Meteo (free, no API key). The chosen city and the last
 * result are cached in SharedPreferences so the widget can draw instantly.
 */
object WeatherRepository {

    data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

    /** Temperatures are always stored in °C; use [temperature] to show them. */
    data class Snapshot(
        val temp: Int,
        val feels: Int,
        val hi: Int,
        val lo: Int,
        val code: Int,
        val humidity: Int,
        val windKmh: Int
    )

    /** One hour of the forecast. [hour] is the local hour of day, 0 to 23. */
    data class Hour(val hour: Int, val temp: Int, val code: Int)

    /** One day of the forecast. [date] is "yyyy-MM-dd" in the city's own time zone. */
    data class Day(val date: String, val hi: Int, val lo: Int, val code: Int)

    /** Hours starting now (every 3 hours), the next 5 days and today's top UV index. */
    data class Forecast(val hours: List<Hour>, val days: List<Day>, val uv: Int?)

    fun useFahrenheit(context: Context): Boolean =
        Glass.prefs(context).getBoolean("w_fahrenheit", false)

    fun toggleUnit(context: Context) {
        Glass.prefs(context).edit().putBoolean("w_fahrenheit", !useFahrenheit(context)).apply()
    }

    /** A °C value converted to the chosen unit. */
    fun temperature(context: Context, celsius: Int): Int =
        if (useFahrenheit(context)) (celsius * 9 / 5.0 + 32).roundToInt() else celsius

    fun unitLabel(context: Context): String = if (useFahrenheit(context)) "°F" else "°C"

    /** Plain-words condition for a WMO weather code. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        in 85..86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    fun savedPlace(context: Context): Place? {
        val p = Glass.prefs(context)
        val lat = p.getString("w_lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("w_lon", null)?.toDoubleOrNull() ?: return null
        return Place(
            p.getString("w_name", "") ?: "",
            p.getString("w_detail", "") ?: "",
            lat,
            lon
        )
    }

    fun savePlace(context: Context, place: Place) {
        Glass.prefs(context).edit()
            .putString("w_name", place.name)
            .putString("w_detail", place.detail)
            .putString("w_lat", place.lat.toString())
            .putString("w_lon", place.lon.toString())
            .remove("w_temp") // old city's numbers no longer apply
            .remove("w_days")
            .remove("w_hours")
            .remove("w_uv")
            .apply()
    }

    fun cached(context: Context): Snapshot? {
        val p = Glass.prefs(context)
        // "w_wind" is checked too, so an older cache without humidity/wind refetches.
        if (!p.contains("w_temp") || !p.contains("w_wind")) return null
        return Snapshot(
            p.getInt("w_temp", 0),
            p.getInt("w_feels", 0),
            p.getInt("w_hi", 0),
            p.getInt("w_lo", 0),
            p.getInt("w_code", 0),
            p.getInt("w_hum", 0),
            p.getInt("w_wind", 0)
        )
    }

    /** The cached forecast, or null until the first download has finished. */
    fun forecast(context: Context): Forecast? {
        val p = Glass.prefs(context)
        val hoursRaw = p.getString("w_hours", null) ?: return null
        val daysRaw = p.getString("w_days", null) ?: return null
        return try {
            val hours = hoursRaw.split(';').filter { it.isNotBlank() }.map {
                val f = it.split('|')
                Hour(f[0].toInt(), f[1].toInt(), f[2].toInt())
            }
            val days = daysRaw.split(';').filter { it.isNotBlank() }.map {
                val f = it.split('|')
                Day(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt())
            }
            Forecast(hours, days, if (p.contains("w_uv")) p.getInt("w_uv", 0) else null)
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking. Call from a background thread. */
    fun searchPlaces(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&name=" +
            URLEncoder.encode(query, "UTF-8")
        val results = JSONObject(httpGet(url)).optJSONArray("results") ?: return emptyList()
        val places = ArrayList<Place>()
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val detail = listOf(r.optString("admin1", ""), r.optString("country", ""))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            places.add(
                Place(
                    r.optString("name", query),
                    detail,
                    r.getDouble("latitude"),
                    r.getDouble("longitude")
                )
            )
        }
        return places
    }

    /** Blocking. Fetches the forecast for the saved city and caches it. */
    fun refresh(context: Context): Boolean {
        val place = savedPlace(context) ?: return false
        return try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,apparent_temperature,weather_code," +
                "relative_humidity_2m,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,uv_index_max" +
                "&forecast_days=5&timezone=auto"
            val json = JSONObject(httpGet(url))
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val edit = Glass.prefs(context).edit()
            putForecast(edit, current.getString("time"), json.getJSONObject("hourly"), daily)
            edit
                .putInt("w_temp", current.getDouble("temperature_2m").roundToInt())
                .putInt("w_feels", current.getDouble("apparent_temperature").roundToInt())
                .putInt("w_code", current.getInt("weather_code"))
                .putInt("w_hum", current.getDouble("relative_humidity_2m").roundToInt())
                .putInt("w_wind", current.getDouble("wind_speed_10m").roundToInt())
                .putInt("w_hi", daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt())
                .putInt("w_lo", daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Packs the hourly and daily arrays into the cache. Throws if the data is incomplete. */
    private fun putForecast(
        edit: android.content.SharedPreferences.Editor,
        nowIso: String,
        hourly: JSONObject,
        daily: JSONObject
    ) {
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        // "2026-11-08T14:15" -> the hour that contains now.
        val nowHour = nowIso.take(13)
        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i).startsWith(nowHour)) { start = i; break }
        }
        val hours = ArrayList<String>()
        var i = start
        while (i < times.length() && hours.size < 6) {
            val hour = times.getString(i).substring(11, 13).toInt()
            hours.add("$hour|${temps.getDouble(i).roundToInt()}|${codes.getInt(i)}")
            i += 3
        }

        val dates = daily.getJSONArray("time")
        val his = daily.getJSONArray("temperature_2m_max")
        val los = daily.getJSONArray("temperature_2m_min")
        val dcodes = daily.getJSONArray("weather_code")
        val days = ArrayList<String>()
        for (d in 0 until minOf(5, dates.length())) {
            days.add(
                "${dates.getString(d)}|${his.getDouble(d).roundToInt()}|" +
                    "${los.getDouble(d).roundToInt()}|${dcodes.getInt(d)}"
            )
        }
        edit.putString("w_hours", hours.joinToString(";"))
        edit.putString("w_days", days.joinToString(";"))
        val uv = daily.optJSONArray("uv_index_max")
        if (uv != null && !uv.isNull(0)) edit.putInt("w_uv", uv.getDouble(0).roundToInt())
        else edit.remove("w_uv")
    }

    /** A simple symbol for a WMO weather code. */
    fun symbolFor(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        in 85..86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
