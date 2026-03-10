package com.ghostpin.app.data

import android.util.Log
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Segment
import com.ghostpin.core.model.SegmentOverrides
import com.ghostpin.core.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting and querying saved routes.
 *
 * Sprint 4 — Tasks 16 & 17.
 *
 * Serialization: Waypoints and Segments are stored as JSON blobs.
 * This avoids a separate join table for what is always read as a unit.
 *
 * JSON schema (Waypoint):
 * ```json
 * { "lat": -23.55, "lng": -46.63, "altitude": 0.0, "bearing": 90.0, "label": "Start" }
 * ```
 *
 * JSON schema (Segment):
 * ```json
 * { "id": "s1", "fromIndex": 0, "toIndex": 1, "distance": 120.5,
 *   "speedOverrideMs": null, "pauseDurationSec": null, "loop": false }
 * ```
 */
@Singleton
class RouteRepository @Inject constructor(
    private val dao: RouteDao,
) {
    companion object {
        private const val TAG = "RouteRepository"
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Observe all saved routes as domain models, newest first. */
    fun observeAll(): Flow<List<Route>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toDomain() } }

    /** One-shot read of a route by ID. Returns null if not found. */
    suspend fun getById(id: String): Route? =
        dao.getById(id)?.toDomain()

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Save a new route. The [Route.id] is used as the primary key.
     *
     * @param route Domain model to persist.
     * @param sourceFormat Origin of the route: "manual", "gpx", "kml", "tcx", "osrm".
     * @return The route ID (same as [Route.id]).
     */
    suspend fun save(route: Route, sourceFormat: String = "manual"): String {
        val entity = route.toEntity(sourceFormat)
        dao.insert(entity)
        Log.d(TAG, "Route saved: id=${route.id} name='${route.name}' source=$sourceFormat")
        return route.id
    }

    /**
     * Update an existing route (full replace by ID).
     */
    suspend fun update(route: Route, sourceFormat: String = "manual") {
        val existing = dao.getById(route.id)
        val entity = route.toEntity(sourceFormat).copy(
            createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.update(entity)
        Log.d(TAG, "Route updated: id=${route.id} name='${route.name}'")
    }

    /**
     * Delete a route by ID. No-op if not found.
     */
    suspend fun deleteById(id: String) {
        dao.deleteById(id)
        Log.d(TAG, "Route deleted: id=$id")
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun Route.toEntity(sourceFormat: String): RouteEntity {
        val waypointsJson = JSONArray().also { arr ->
            waypoints.forEach { wp ->
                arr.put(JSONObject().apply {
                    put("lat", wp.lat)
                    put("lng", wp.lng)
                    put("altitude", wp.altitude)
                    put("bearing", wp.bearing.toDouble())
                    put("label", wp.label ?: JSONObject.NULL)
                })
            }
        }.toString()

        val segmentsJson = JSONArray().also { arr ->
            segments.forEach { seg ->
                arr.put(JSONObject().apply {
                    put("id", seg.id)
                    put("fromIndex", seg.fromIndex)
                    put("toIndex", seg.toIndex)
                    put("distance", seg.distance)
                    put("speedOverrideMs", seg.overrides?.speedOverrideMs ?: JSONObject.NULL)
                    put("pauseDurationSec", seg.overrides?.pauseDurationSec ?: JSONObject.NULL)
                    put("loop", seg.overrides?.loop ?: false)
                })
            }
        }.toString()

        // Compute total distance from waypoints if not cached in segments
        val totalDist = if (segments.isNotEmpty()) {
            segments.sumOf { it.distance }
        } else {
            haversineTotal(waypoints)
        }

        val now = System.currentTimeMillis()
        return RouteEntity(
            id = id,
            name = name,
            waypointsJson = waypointsJson,
            segmentsJson = segmentsJson,
            sourceFormat = sourceFormat,
            totalDistanceMeters = totalDist,
            createdAtMs = now,
            updatedAtMs = now,
        )
    }

    private fun RouteEntity.toDomain(): Route? = runCatching {
        val waypointArr = JSONArray(waypointsJson)
        val waypoints = (0 until waypointArr.length()).map { i ->
            val obj = waypointArr.getJSONObject(i)
            Waypoint(
                lat = obj.getDouble("lat"),
                lng = obj.getDouble("lng"),
                altitude = obj.optDouble("altitude", 0.0),
                bearing = obj.optDouble("bearing", 0.0).toFloat(),
                label = obj.optString("label").takeIf { it.isNotBlank() && it != "null" },
            )
        }

        val segmentArr = JSONArray(segmentsJson)
        val segments = (0 until segmentArr.length()).map { i ->
            val obj = segmentArr.getJSONObject(i)
            val speedOverride = if (obj.isNull("speedOverrideMs")) null else obj.getDouble("speedOverrideMs")
            val pauseDuration = if (obj.isNull("pauseDurationSec")) null else obj.getDouble("pauseDurationSec")
            val loop = obj.optBoolean("loop", false)
            val overrides = if (speedOverride != null || pauseDuration != null || loop) {
                SegmentOverrides(speedOverride, pauseDuration, loop)
            } else null

            Segment(
                id = obj.getString("id"),
                fromIndex = obj.getInt("fromIndex"),
                toIndex = obj.getInt("toIndex"),
                distance = obj.getDouble("distance"),
                overrides = overrides,
            )
        }

        Route(id = id, name = name, waypoints = waypoints, segments = segments)
    }.onFailure { e ->
        Log.e(TAG, "Failed to deserialize route id=$id: ${e.message}")
    }.getOrNull()

    // ── Haversine helper ──────────────────────────────────────────────────────

    private fun haversineTotal(waypoints: List<Waypoint>): Double {
        if (waypoints.size < 2) return 0.0
        return waypoints.zipWithNext().sumOf { (a, b) ->
            val R = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLng = Math.toRadians(b.lng - a.lng)
            val sinDLat = Math.sin(dLat / 2)
            val sinDLng = Math.sin(dLng / 2)
            val c = sinDLat * sinDLat +
                    Math.cos(Math.toRadians(a.lat)) *
                    Math.cos(Math.toRadians(b.lat)) *
                    sinDLng * sinDLng
            R * 2 * Math.atan2(Math.sqrt(c), Math.sqrt(1 - c))
        }
    }
}
