package com.ghostpin.core.model

/**
 * A route to simulate, composed of ordered waypoints and segments between them.
 */
data class Route(
    val id: String,
    val name: String,
    val waypoints: List<Waypoint>,
    val segments: List<Segment> = emptyList(),
) {
    init {
        require(waypoints.size >= 2) { "Route must have at least 2 waypoints" }
    }
}

/**
 * A single point on a route.
 */
data class Waypoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val label: String? = null,
)

/**
 * A segment between two consecutive waypoints.
 */
data class Segment(
    val id: String,
    val fromIndex: Int,
    val toIndex: Int,
    val distance: Double,          // meters
    val overrides: SegmentOverrides? = null,
)

/**
 * Optional per-segment overrides for speed, pause, etc.
 */
data class SegmentOverrides(
    val speedOverrideMs: Double? = null,
    val pauseDurationSec: Double? = null,
    val loop: Boolean = false,
)

/** Extension property to calculate the total distance of the route. */
val Route.distanceMeters: Double
    get() {
        if (segments.isNotEmpty()) {
            return segments.sumOf { it.distance }
        }
        // Fallback: simple haversine sum if segments aren't fully populated yet.
        var dist = 0.0
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val R = 6378137.0 // Earth radius in meters
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lng - a.lng)
            val aVal = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(aVal), Math.sqrt(1 - aVal))
            dist += R * c
        }
        return dist
    }

/** Extension property for compatibility with legacy distance variables. */
val Route.durationSeconds: Double
    get() = 0.0
