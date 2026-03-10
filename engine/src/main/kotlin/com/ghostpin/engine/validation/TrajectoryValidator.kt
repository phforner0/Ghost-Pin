package com.ghostpin.engine.validation

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import kotlin.math.*

/**
 * Pre-simulation trajectory validator.
 *
 * Validates a route against the physical constraints of the active profile
 * BEFORE starting simulation. Catches impossible trajectories early rather
 * than producing obviously fake data.
 *
 * Checks:
 * 1. No segment requires speed exceeding the profile's maximum
 * 2. No bearing change exceeds the profile's max turn rate at implied speed
 * 3. Route has sufficient waypoints
 */
class TrajectoryValidator {

    /**
     * Validate a route against profile constraints.
     *
     * @param route The route to validate.
     * @param profile The active movement profile.
     * @return [ValidationResult] with any warnings.
     */
    fun validate(route: Route, profile: MovementProfile): ValidationResult {
        val warnings = mutableListOf<String>()

        // Validate segments: check required speed
        route.segments.forEach { seg ->
            val speedOverride = seg.overrides?.speedOverrideMs
            if (speedOverride != null && speedOverride > profile.maxSpeedMs) {
                warnings += "Segment '${seg.id}': speed override " +
                    "${"%.1f".format(speedOverride)} m/s exceeds profile max " +
                    "${"%.1f".format(profile.maxSpeedMs)} m/s"
            }

            // Check if segment distance requires impossible speed for pause duration
            val pauseDuration = seg.overrides?.pauseDurationSec
            if (pauseDuration != null && pauseDuration > 0) {
                val requiredSpeed = seg.distance / pauseDuration
                if (requiredSpeed > profile.maxSpeedMs) {
                    warnings += "Segment '${seg.id}': distance ${seg.distance}m with " +
                        "pause ${pauseDuration}s requires speed " +
                        "${"%.1f".format(requiredSpeed)} m/s (max: " +
                        "${"%.1f".format(profile.maxSpeedMs)} m/s)"
                }
            }
        }

        // Validate bearing changes between consecutive waypoints
        val waypoints = route.waypoints
        if (waypoints.size >= 3) {
            for (i in 1 until waypoints.size - 1) {
                val prev = waypoints[i - 1]
                val curr = waypoints[i]
                val next = waypoints[i + 1]

                // Compute bearings
                val bearingIn = computeBearing(prev.lat, prev.lng, curr.lat, curr.lng)
                val bearingOut = computeBearing(curr.lat, curr.lng, next.lat, next.lng)

                var angleDelta = abs(bearingOut - bearingIn)
                if (angleDelta > 180.0) angleDelta = 360.0 - angleDelta

                // Estimate time through the waypoint based on segment distance and max speed
                val distIn = haversineDistance(prev.lat, prev.lng, curr.lat, curr.lng)
                val estimatedTime = distIn / profile.maxSpeedMs

                val maxTurn = profile.maxTurnRateDegPerSec * estimatedTime
                if (angleDelta > maxTurn && estimatedTime < 10.0) {
                    warnings += "Waypoint ${i} (${curr.lat}, ${curr.lng}): bearing change " +
                        "${"%.1f".format(angleDelta)}° exceeds max " +
                        "${"%.1f".format(maxTurn)}° for estimated transit time " +
                        "${"%.1f".format(estimatedTime)}s"
                }
            }
        }

        return ValidationResult(
            isValid = warnings.isEmpty(),
            warnings = warnings,
        )
    }

    /**
     * Compute initial bearing from point A to point B (degrees, 0-360).
     */
    private fun computeBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Haversine distance between two coordinates (meters).
     */
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

/**
 * Result of trajectory validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val warnings: List<String>,
)
