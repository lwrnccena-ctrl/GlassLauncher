package com.fares.glass.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds where the TV is from its internet address (no GPS, no permission). It is as accurate as the
 * internet provider's records: usually the right city, sometimes a nearby one.
 */
object Locator {

    /** label = what the city picker shows (name, region, country); city = the short name shown by the weather. */
    class Place(val lat: Double, val lon: Double, val city: String, val zone: String, val label: String = city)

    /** Free services without a key. The next one is tried when one fails. */
    private val services = listOf(
        "https://ipwho.is/",
        "https://get.geojs.io/v1/ip/geo.json",
        "https://ipinfo.io/json",
        "https://ipapi.co/json/",
    )

    /** Returns the place, or null if every service failed (no internet, or the services refused the TV). */
    suspend fun detect(): Place? = withContext(Dispatchers.IO) {
        for (url in services) {
            try {
                val body = get(url)
                val o = JSONObject(body)
                // ipwho.is says success = false when it cannot answer.
                if (o.has("success") && !o.optBoolean("success", true)) {
                    Log.w("Glass", "Location service refused: $url -> ${body.take(200)}")
                    continue
                }
                val found = place(o)
                if (found != null) return@withContext found
                Log.w("Glass", "Location service gave no coordinates: $url -> ${body.take(200)}")
            } catch (e: Exception) {
                Log.w("Glass", "Location service failed: $url", e)
            }
        }
        Log.w("Glass", "No location service gave an answer")
        null
    }

    /**
     * The name of the city at these coordinates (BigDataCloud's free reverse geocoding, no key).
     * Used when the internet address gave coordinates but no city name. Null if it could not be found.
     */
    suspend fun cityFor(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(
                get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"),
            )
            fun text(key: String) = if (o.isNull(key)) "" else o.optString(key, "")
            text("city").ifBlank { text("locality") }.ifBlank { text("principalSubdivision") }.ifBlank { null }
        } catch (e: Exception) {
            Log.w("Glass", "Reverse geocoding failed", e)
            null
        }
    }

    /** Places that match a name, for the city picker (Open-Meteo's free place search, no key). */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json"))
            val results = o.optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).mapNotNull { i ->
                val r = results.getJSONObject(i)
                fun text(key: String) = if (r.isNull(key)) "" else r.optString(key, "")
                val lat = r.optDouble("latitude", Double.NaN)
                val lon = r.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
                val name = text("name")
                if (name.isBlank()) return@mapNotNull null
                val label = listOf(name, text("admin1"), text("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
                Place(lat, lon, name, text("timezone").ifBlank { "Asia/Manila" }, label)
            }
        } catch (e: Exception) {
            Log.w("Glass", "Place search failed", e)
            emptyList()
        }
    }

    private fun place(o: JSONObject): Place? {
        fun text(key: String) = if (o.isNull(key)) "" else o.optString(key, "")

        var lat = o.optDouble("latitude", Double.NaN)
        var lon = o.optDouble("longitude", Double.NaN)
        // ipinfo.io gives "loc": "14.6042,120.9822"
        if ((lat.isNaN() || lon.isNaN()) && text("loc").contains(",")) {
            val parts = text("loc").split(",")
            lat = parts[0].trim().toDoubleOrNull() ?: Double.NaN
            lon = parts[1].trim().toDoubleOrNull() ?: Double.NaN
        }
        if (lat.isNaN() || lon.isNaN() || (lat == 0.0 && lon == 0.0)) return null

        val city = text("city").ifBlank { text("region") }
        // ipwho.is: "timezone": {"id": "Asia/Manila", ...}   the others: "timezone": "Asia/Manila"
        val zone = o.optJSONObject("timezone")?.optString("id", "").orEmpty().ifBlank { text("timezone") }
        return Place(lat, lon, city, zone.ifBlank { "Asia/Manila" })
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            // Some of these services turn away requests that do not look like a normal browser.
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
            )
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from $url")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
