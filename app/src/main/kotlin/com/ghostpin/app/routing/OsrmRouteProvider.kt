package com.ghostpin.app.routing

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches street-snapped routes from the public OSRM demo server.
 *
 * No API key required. The public demo server is rate-limited — for production
 * use a self-hosted OSRM instance or replace the base URL with another routing
 * provider (GraphHopper, Valhalla, etc.) that supports the same GeoJSON output.
 *
 * OSRM profile mapping:
 *   Pedestrian   → foot
 *   Bicycle      → bike
 *   Car          → car
 *   UrbanVehicle → car
 *   Drone        → null  (returns straight-line fallback — drones ignore roads)
 *
 * All network I/O runs on [Dispatchers.IO] and is fully suspendable.
 */
@Singleton
class OsrmRouteProvider @Inject constructor() {

    companion object {
        private const val BASE_URL     = "https://router.project-osrm.org/route/v1"
        private const val TIMEOUT_MS   = 8_000
        private const val MIN_WAYPOINTS = 2
    }

    /**
     * Fetch a street-snapped route between two points.
     *
     * @param startLat  Start latitude.
     * @param startLng  Start longitude.
     * @param endLat    End latitude.
     * @param endLng    End longitude.
     * @param profile   [MovementProfile] to determine the road network (foot/bike/car).
     * @return [Result.success] with a [Route] containing all intermediate geometry points,
     *         or [Result.failure] if the network call fails or OSRM returns an error.
     */
    suspend fun fetchRoute(
        startLat: Double,
        startLng: Double,
        endLat:   Double,
        endLng:   Double,
        profile:  MovementProfile,
    ): Result<Route> = withContext(Dispatchers.IO) {
        val osrmProfile = profile.toOsrmProfile()

        // Drones don't follow roads — skip OSRM entirely
        if (osrmProfile == null) {
            return@withContext Result.success(fallbackRoute(startLat, startLng, endLat, endLng))
        }

        runCatching {
            val url = buildUrl(osrmProfile, startLng, startLat, endLng, endLat)
            val json = httpGet(url)
            parseOsrmResponse(json)
        }
    }

    /**
     * Build a straight-line [Route] between two points.
     * Used as fallback when OSRM is unavailable or for the Drone profile.
     */
    fun fallbackRoute(
        startLat: Double,
        startLng: Double,
        endLat:   Double,
        endLng:   Double,
    ): Route = Route(
        id   = "fallback-${System.currentTimeMillis()}",
        name = "Direct Route",
        waypoints = listOf(
            Waypoint(lat = startLat, lng = startLng),
            Waypoint(lat = endLat,   lng = endLng),
        ),
    )

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildUrl(
        osrmProfile: String,
        startLng: Double, startLat: Double,
        endLng:   Double, endLat:   Double,
    ): String {
        // overview=full   → full geometry, not simplified
        // geometries=geojson → coordinate array as [lng, lat]
        // steps=false     → no turn-by-turn, just polyline
        return "$BASE_URL/$osrmProfile/" +
            "$startLng,$startLat;$endLng,$endLat" +
            "?overview=full&geometries=geojson&steps=false"
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod         = "GET"
            connectTimeout        = TIMEOUT_MS
            readTimeout           = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GhostPin/3.0 (location-simulation)")
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseOsrmResponse(json: String): Route {
        val root = JSONObject(json)
        val code = root.optString("code", "")

        if (code != "Ok") {
            val message = root.optString("message", "unknown OSRM error")
            error("OSRM returned non-OK status: code=$code message=$message")
        }

        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) error("OSRM returned no routes")

        val coords = routes
            .getJSONObject(0)
            .getJSONObject("geometry")
            .getJSONArray("coordinates")

        if (coords.length() < MIN_WAYPOINTS) {
            error("OSRM geometry has fewer than $MIN_WAYPOINTS points (got ${coords.length()})")
        }

        val waypoints = (0 until coords.length()).map { i ->
            val pt = coords.getJSONArray(i)
            // GeoJSON order: [longitude, latitude]
            Waypoint(
                lat = pt.getDouble(1),
                lng = pt.getDouble(0),
            )
        }

        return Route(
            id        = "osrm-${System.currentTimeMillis()}",
            name      = "OSRM Route (${waypoints.size} pts)",
            waypoints = waypoints,
        )
    }

    // ── MovementProfile → OSRM profile ──────────────────────────────────────

    /**
     * Returns the OSRM routing profile for a given [MovementProfile],
     * or null if the profile should not use road routing (e.g. Drone).
     */
    private fun MovementProfile.toOsrmProfile(): String? = when (name) {
        "Pedestrian"    -> "foot"
        "Bicycle"       -> "bike"
        "Car"           -> "car"
        "Urban Vehicle" -> "car"
        "Drone"         -> null   // Straight line — no roads
        else            -> "foot" // Safe default
    }
}
