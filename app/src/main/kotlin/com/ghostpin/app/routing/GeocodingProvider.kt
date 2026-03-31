package com.ghostpin.app.routing

import android.util.Log
import com.ghostpin.core.security.LogSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class GeocodingProvider
    @Inject
    constructor() {
        companion object {
            private const val TAG = "GeocodingProvider"
            private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
            private const val TIMEOUT_MS = 8_000
            private const val MAX_RESULTS = 5
            private const val MIN_REQUEST_INTERVAL_MS = 1_000L
        }

        private val rateLimitMutex = Mutex()
        private val cachedResults = LinkedHashMap<String, List<GeoResult>>()

        @Volatile private var lastRequestAtMs: Long = 0L

        @Volatile private var lastFailureMessage: String? = null

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
        suspend fun search(
            query: String,
            limit: Int = MAX_RESULTS
        ): List<GeoResult> =
            withContext(Dispatchers.IO) {
                if (query.isBlank()) return@withContext emptyList()

                val normalizedQuery = query.trim().lowercase()
                val cacheKey = "$normalizedQuery:$limit"

                runCatching {
                    rateLimitMutex.withLock {
                        cachedResults[cacheKey]?.let { return@withLock it }
                        val now = System.currentTimeMillis()
                        val waitMs = (MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMs)).coerceAtLeast(0L)
                        if (waitMs > 0L) delay(waitMs)
                        lastRequestAtMs = System.currentTimeMillis()
                        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                        val url = "$BASE_URL?q=$encoded&format=json&limit=$limit&addressdetails=0"
                        val json = httpGet(url)
                        parseResults(json).also {
                            cachedResults[cacheKey] = it
                            trimCacheLocked()
                            lastFailureMessage = null
                        }
                    }
                }.getOrElse { e ->
                    lastFailureMessage = "Geocoding unavailable. Check your connection and try again."
                    Log.w(
                        TAG,
                        LogSanitizer.sanitizeString("Geocoding failed for query '${query.take(64)}': ${e.message}")
                    )
                    emptyList()
                }
            }

        fun consumeLastFailureMessage(): String? = lastFailureMessage.also { lastFailureMessage = null }

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

        private fun trimCacheLocked(maxEntries: Int = 16) {
            while (cachedResults.size > maxEntries) {
                val firstKey = cachedResults.entries.firstOrNull()?.key ?: break
                cachedResults.remove(firstKey)
            }
        }
    }
