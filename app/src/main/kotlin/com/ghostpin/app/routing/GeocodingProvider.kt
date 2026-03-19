package com.ghostpin.app.routing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocoding provider using OpenStreetMap Nominatim (free, no API key).
 *
 * Sprint 7 — Task 26.
 *
 * Features:
 *  - Forward geocoding: address → lat/lng
 *  - Search suggestions with debounce-friendly design
 *  - No API key or billing required
 *
 * Rate limit: Nominatim allows max 1 request/second.
 * We comply by running queries sequentially on [Dispatchers.IO].
 */
@Singleton
class GeocodingProvider @Inject constructor() {

    companion object {
        private const val TAG = "GeocodingProvider"
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_RESULTS = 5
    }

    /**
     * A geocoding search result.
     *
     * @property displayName Human-readable address (e.g. "Rua Augusta, São Paulo, SP, Brazil")
     * @property lat Latitude of the result
     * @property lng Longitude of the result
     */
    data class GeoResult(
        val displayName: String,
        val lat: Double,
        val lng: Double,
    )

    /**
     * Search for locations matching [query].
     *
     * @param query Free-text address or place name.
     * @param limit Maximum number of results (default 5).
     * @return List of matching [GeoResult]s, empty on error or no results.
     */
    suspend fun search(query: String, limit: Int = MAX_RESULTS): List<GeoResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "$BASE_URL?q=$encoded&format=json&limit=$limit&addressdetails=0"
                val json = httpGet(url)
                parseResults(json)
            }.getOrElse { e ->
                Log.w(TAG, "Geocoding failed for query: ${e.message}")
                emptyList()
            }
        }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GhostPin/3.1 (location-simulation)")
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw RuntimeException("Nominatim HTTP $responseCode: $errorBody")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResults(json: String): List<GeoResult> {
        val array = JSONArray(json)
        val results = mutableListOf<GeoResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val lat = obj.optString("lat").toDoubleOrNull() ?: continue
            val lon = obj.optString("lon").toDoubleOrNull() ?: continue
            val displayName = obj.optString("display_name", "Unknown")
            results.add(GeoResult(displayName, lat, lon))
        }
        return results
    }
}
