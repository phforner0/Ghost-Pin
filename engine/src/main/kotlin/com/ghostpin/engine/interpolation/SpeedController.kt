package com.ghostpin.engine.interpolation

import com.ghostpin.core.model.MovementProfile
import kotlin.math.max
import kotlin.math.min

/**
 * Controls the simulated speed along a route.
 *
 * Features:
 * - Smooth acceleration / deceleration governed by [MovementProfile.maxAccelMs2]
 * - Real-time adjustable [targetRatio] (0..1 × profile.maxSpeedMs)
 * - Automatic slowdown within [WAYPOINT_SLOWDOWN_RADIUS] metres of the next waypoint,
 *   producing physically realistic cornering behaviour
 *
 * Usage per frame:
 *   val metersThisFrame = controller.advance(deltaTimeSec, distToNextWaypoint)
 *   val currentSpeed    = controller.currentSpeedMs
 */
class SpeedController(
    private val profile: MovementProfile,
    initialRatio: Double = DEFAULT_CRUISE_RATIO,
) {
    companion object {
        /** Default cruise speed: 65 % of profile maximum. */
        const val DEFAULT_CRUISE_RATIO = 0.65

        /**
         * Radius (metres) within which the controller begins decelerating
         * ahead of the next waypoint.
         */
        const val WAYPOINT_SLOWDOWN_RADIUS = 20.0

        /**
         * Minimum speed ratio when passing directly over a waypoint (20 % of cruise).
         * Prevents a full stop at every intermediate point.
         */
        const val WAYPOINT_MIN_RATIO = 0.20
    }

    /**
     * Current speed in m/s. Always in [0, profile.maxSpeedMs].
     * Readable at any time for injecting into [com.ghostpin.core.model.MockLocation.speed].
     */
    var currentSpeedMs: Double = 0.0
        private set

    /**
     * Target cruise ratio [0.0, 1.0] relative to [MovementProfile.maxSpeedMs].
     * Can be changed at any time for real-time speed control.
     * Setting this to 0.0 causes the controller to brake to a stop.
     */
    var targetRatio: Double = initialRatio.coerceIn(0.0, 1.0)
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** Computed target speed (m/s) from the current ratio. */
    private val targetSpeedMs: Double
        get() = profile.maxSpeedMs * targetRatio

    /**
     * Advance the controller by [deltaTimeSec] seconds and return how many
     * metres to move along the route this frame.
     *
     * @param deltaTimeSec         Seconds since the last frame (e.g. 0.2 for 5 Hz).
     * @param distToNextWaypoint   Remaining metres to the next waypoint.
     *                             Pass [Double.MAX_VALUE] if waypoint proximity is irrelevant.
     * @return Distance in metres to advance along the route this frame.
     */
    fun advance(
        deltaTimeSec: Double,
        distToNextWaypoint: Double = Double.MAX_VALUE,
    ): Double {
        require(deltaTimeSec > 0.0) { "deltaTimeSec must be positive" }

        val desired = desiredSpeed(distToNextWaypoint)
        val maxDelta = profile.maxAccelMs2 * deltaTimeSec

        currentSpeedMs =
            when {
                desired > currentSpeedMs -> min(currentSpeedMs + maxDelta, desired)
                desired < currentSpeedMs -> max(currentSpeedMs - maxDelta, desired)
                else -> desired
            }.coerceIn(0.0, profile.maxSpeedMs)

        return currentSpeedMs * deltaTimeSec
    }

    /**
     * Computes the desired speed (m/s) for this frame, accounting for
     * upcoming waypoint proximity.
     */
    private fun desiredSpeed(distToNextWaypoint: Double): Double {
        if (distToNextWaypoint >= WAYPOINT_SLOWDOWN_RADIUS) return targetSpeedMs

        // Linear interpolation from minRatio at dist=0 to targetRatio at dist=slowdownRadius
        val fraction = (distToNextWaypoint / WAYPOINT_SLOWDOWN_RADIUS).coerceIn(0.0, 1.0)
        val ratio = WAYPOINT_MIN_RATIO + (targetRatio - WAYPOINT_MIN_RATIO) * fraction
        return profile.maxSpeedMs * ratio
    }

    /**
     * Reset speed state to zero — call before starting a new simulation.
     */
    fun reset() {
        currentSpeedMs = 0.0
    }

    /**
     * Convenience: estimate total travel time in seconds for [distanceMeters]
     * at cruise speed (ignores acceleration ramp). Useful for initial totalFrames estimate.
     */
    fun estimateDurationSec(distanceMeters: Double): Double {
        val cruiseSpeed = profile.maxSpeedMs * targetRatio
        return if (cruiseSpeed > 0.0) distanceMeters / cruiseSpeed else Double.MAX_VALUE
    }
}
