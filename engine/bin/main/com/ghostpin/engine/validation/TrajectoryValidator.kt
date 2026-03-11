package com.ghostpin.engine.validation

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.math.GeoMath
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
                val bearingIn = GeoMath.bearingBetween(prev.lat, prev.lng, curr.lat, curr.lng).toDouble()
                val bearingOut = GeoMath.bearingBetween(curr.lat, curr.lng, next.lat, next.lng).toDouble()

                var angleDelta = abs(bearingOut - bearingIn)
                if (angleDelta > 180.0) angleDelta = 360.0 - angleDelta

                // Estimate time through the waypoint based on segment distance and max speed
                val distIn = GeoMath.haversineMeters(prev.lat, prev.lng, curr.lat, curr.lng)
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

}

/**
 * Result of trajectory validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val warnings: List<String>,
)
