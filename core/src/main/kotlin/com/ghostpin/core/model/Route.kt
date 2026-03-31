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
    val distance: Double, // meters
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
            val earthRadiusMeters = 6378137.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lng - a.lng)
            val aVal =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(aVal), Math.sqrt(1 - aVal))
            dist += earthRadiusMeters * c
        }
        return dist
    }

/**
 * Estimates route travel time in seconds for a given [MovementProfile].
 *
 * Uses 80% of [MovementProfile.maxSpeedMs] as the nominal cruise speed,
 * which is realistic for GPS simulation (accounts for stops, turns, etc.).
 * Per-segment [SegmentOverrides.speedOverrideMs] is respected when set.
 *
 * If segments are not populated, falls back to total haversine distance
 * with the same formula applied uniformly.
 *
 * @param profile The movement profile to estimate with.
 * @return Estimated travel time in seconds (excludes waypoint pause durations).
 */
fun Route.estimateDuration(profile: MovementProfile): Double {
    val cruiseSpeed = profile.maxSpeedMs * 0.80 // realistic cruise at 80% of peak

    if (segments.isNotEmpty()) {
        return segments.sumOf { segment ->
            val speed = segment.overrides?.speedOverrideMs ?: cruiseSpeed
            if (speed > 0) segment.distance / speed else 0.0
        }
    }

    // Fallback: use total distance at cruise speed
    return if (cruiseSpeed > 0) distanceMeters / cruiseSpeed else 0.0
}

/**
 * Legacy stub kept for binary compatibility — always returns 0.0.
 * Use [estimateDuration] for real estimates.
 */
val Route.durationSeconds: Double
    get() = 0.0

/**
 * Formats a duration in seconds as a human-readable string.
 * e.g. 75s -> "1m 15s", 3720s -> "1h 2m"
 */
fun formatDuration(seconds: Double): String {
    val totalSec = seconds.toLong().coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
