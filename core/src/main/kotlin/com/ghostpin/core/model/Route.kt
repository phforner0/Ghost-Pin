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
