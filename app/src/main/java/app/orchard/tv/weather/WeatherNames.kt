package app.orchard.tv.weather

import org.json.JSONObject
import java.util.Locale

/** Name localization must not change the location used for the forecast. */
internal object WeatherNames {
    fun language(locale: Locale = Locale.getDefault()): String =
        locale.language.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"

    fun nearbyName(response: JSONObject, lat: Double, lon: Double): String? {
        val results = response.optJSONArray("results") ?: return null
        return (0 until results.length()).map { results.getJSONObject(it) }
            .filter { it.optString("name").isNotBlank() }
            .minByOrNull { Cities.distanceKm(lat, lon, it.optDouble("latitude"), it.optDouble("longitude")) }
            ?.takeIf { Cities.distanceKm(lat, lon, it.optDouble("latitude"), it.optDouble("longitude")) <= 1.0 }
            ?.getString("name")
    }
}
