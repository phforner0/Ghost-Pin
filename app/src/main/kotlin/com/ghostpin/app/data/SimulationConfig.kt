package com.ghostpin.app.data

import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of a launchable simulation session.
 *
 * Stored in [SimulationRepository] so that quick-start surfaces
 * (QS Tile, Widget, Automation) can launch a simulation without
 * requiring the user to re-select settings.
 *
 * @property profileName Name of the [com.ghostpin.core.model.MovementProfile] (e.g. "Car").
 * @property startLat    Starting latitude.
 * @property startLng    Starting longitude.
 * This model is intentionally richer than the original quick-start config so the
 * app can reuse the same launch plan across the main UI, favorites, history,
 * schedules, QS tile, widget, and automation surfaces.
 */
data class SimulationConfig(
    val profileName: String,
    val profileLookupKey: String = profileName,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double = DefaultCoordinates.END_LAT,
    val endLng: Double = DefaultCoordinates.END_LNG,
    val routeId: String? = null,
    val appMode: AppMode = AppMode.CLASSIC,
    val waypoints: List<Waypoint> = emptyList(),
    val waypointPauseSec: Double = 0.0,
    val speedRatio: Double = 1.0,
    val frequencyHz: Int = 5,
    val repeatPolicy: RepeatPolicy = RepeatPolicy.NONE,
    val repeatCount: Int = 1
) {
    fun serializedWaypoints(): String = serializeWaypoints(waypoints)

    companion object {
        fun serializeWaypoints(waypoints: List<Waypoint>): String {
            val array = JSONArray()
            waypoints.forEach { waypoint ->
                array.put(
                    JSONObject().apply {
                        put("lat", waypoint.lat)
                        put("lng", waypoint.lng)
                    }
                )
            }
            return array.toString()
        }

        fun deserializeWaypoints(serialized: String?): List<Waypoint> {
            if (serialized.isNullOrBlank()) return emptyList()

            return runCatching {
                val array = JSONArray(serialized)
                buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val lat = item.optDouble("lat", Double.NaN)
                        val lng = item.optDouble("lng", Double.NaN)
                        if (lat.isFinite() && lng.isFinite()) {
                            add(Waypoint(lat = lat, lng = lng))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
