package com.fares.glass.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** The weather at one hour of a day. */
data class HourForecast(val hour: Int, val tempC: Int, val code: Int)

/** The forecast for one day. label = "Today", "Tomorrow", then the weekday name. */
data class DayForecast(
    val label: String,
    val maxC: Int,
    val minC: Int,
    val code: Int,
    val hours: List<HourForecast>,
)

data class Weather(
    val tempC: Int,
    val code: Int,
    val sunrise: Long,
    val sunset: Long,
    val isPlaceholder: Boolean = false,
    /** Name of the place (the detected city). Empty = no name known. */
    val place: String = "",
    /** Time zone of the place, for example "Asia/Manila". */
    val zone: String = "Asia/Manila",
    /** Today and the next days (empty if the forecast could not be read). */
    val days: List<DayForecast> = emptyList(),
) {
    /** Drizzle, rain, showers, thunderstorm. */
    val isRaining get() = code in 51..67 || code in 80..82 || code in 95..99

    companion object {
        /** Shown before the first real answer arrives. */
        val Placeholder = Weather(0, 0, 0L, 0L, isPlaceholder = true)
    }
}

object WeatherRepository {
    const val DEFAULT_LAT = 13.6663
    const val DEFAULT_LON = 121.1030

    /** The sky wallpaper (SkyWallpaper.kt) counts its day in Manila time. */
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    /** Returns null when offline or on any error. Call from a coroutine. */
    suspend fun fetch(lat: Double = DEFAULT_LAT, lon: Double = DEFAULT_LON): Weather? =
        withContext(Dispatchers.IO) {
            try {
                // timezone=auto: times come in the local time of the place, wherever it is.
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=sunrise,sunset,weather_code,temperature_2m_max,temperature_2m_min" +
                        "&hourly=temperature_2m,weather_code" +
                        "&timezone=auto&forecast_days=7"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
                val json = JSONObject(body)
                val cur = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                val zone = json.optString("timezone", "Asia/Manila").ifBlank { "Asia/Manila" }
                val zoneTz = TimeZone.getTimeZone(zone)
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = zoneTz }
                Weather(
                    tempC = cur.getDouble("temperature_2m").roundToInt(),
                    code = cur.getInt("weather_code"),
                    sunrise = fmt.parse(daily.getJSONArray("sunrise").getString(0))!!.time,
                    sunset = fmt.parse(daily.getJSONArray("sunset").getString(0))!!.time,
                    zone = zone,
                    days = readDays(json, zoneTz),
                )
            } catch (e: Exception) {
                null
            }
        }

    /** The next days with their hours. If anything is missing the list is empty (the rest still works). */
    private fun readDays(json: JSONObject, zoneTz: TimeZone): List<DayForecast> = try {
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val codes = daily.getJSONArray("weather_code")

        val hourly = json.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemps = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val byDate = HashMap<String, MutableList<HourForecast>>()
        for (i in 0 until hTimes.length()) {
            if (hTemps.isNull(i) || hCodes.isNull(i)) continue
            val t = hTimes.getString(i) // for example 2026-10-28T03:00
            byDate.getOrPut(t.substring(0, 10)) { ArrayList() }
                .add(HourForecast(t.substring(11, 13).toInt(), hTemps.getDouble(i).roundToInt(), hCodes.getInt(i)))
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zoneTz }
        val dayFmt = SimpleDateFormat("EEEE", Locale.getDefault()).apply { timeZone = zoneTz }
        (0 until dates.length()).mapNotNull { i ->
            if (maxs.isNull(i) || mins.isNull(i) || codes.isNull(i)) return@mapNotNull null
            val date = dates.getString(i)
            val label = when (i) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> dayFmt.format(dateFmt.parse(date)!!)
            }
            DayForecast(
                label, maxs.getDouble(i).roundToInt(), mins.getDouble(i).roundToInt(), codes.getInt(i),
                byDate[date].orEmpty(),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * How many milliseconds to add to times so that SkyWallpaper (which counts in Manila time) sees the
     * local day of the place with this time zone. 0 for the Philippines.
     */
    fun skewMs(zoneId: String, now: Long = System.currentTimeMillis()): Long =
        tz.getOffset(now).toLong() - TimeZone.getTimeZone(zoneId).getOffset(now).toLong()

    /** Offline fallback: about 5:40 AM and 5:50 PM, close to the Philippines year-round. */
    fun fallbackTimes(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        fun at(h: Int, m: Int) = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return at(5, 40) to at(17, 50)
    }
}
