package com.ghostpin.engine.noise

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import kotlin.math.abs

/**
 * Post-processing filter ensuring Location metadata is internally coherent
 * with what physical sensors would produce. This is the Gap 3 mitigation.
 *
 * Without real sensor injection (VirtualDevice API requires root), we ensure
 * the fields of the Location object itself don't betray the simulation:
 *
 * - **Speed**: Clamped by maximum acceleration — never jumps more than maxAccel·Δt
 * - **Bearing**: Interpolated proportionally to speed — no 180° flips at high speed
 * - **Accuracy**: Degrades with speed (+2m at 80km/h), worse during multipath jumps
 * - **Altitude**: Variation bounded by profile-specific sigma
 *
 * @param profile The active movement profile for physical constraints.
 */
class SensorCoherenceFilter(
    private val profile: MovementProfile,
) {
    private var previousSpeed: Float = 0f
    private var previousBearing: Float = 0f
    private var previousAltitude: Double = 0.0
    private var isFirstFrame: Boolean = true

    /**
     * Apply coherence constraints to a mock location.
     *
     * @param location The raw mock location (after noise application).
     * @param deltaTimeSec Time since last frame.
     * @param wasJump Whether this frame had a multipath jump.
     * @return Corrected [MockLocation] with coherent metadata.
     */
    fun apply(
        location: MockLocation,
        deltaTimeSec: Double,
        wasJump: Boolean
    ): MockLocation {
        if (isFirstFrame) {
            previousSpeed = location.speed
            previousBearing = location.bearing
            previousAltitude = location.altitude
            isFirstFrame = false
            return location
        }

        // --- Speed clamping by acceleration limit ---
        val maxSpeedDelta = (profile.maxAccelMs2 * deltaTimeSec).toFloat()
        val clampedSpeed =
            clampDelta(location.speed, previousSpeed, maxSpeedDelta)
                .coerceIn(0f, profile.maxSpeedMs.toFloat())

        // --- Bearing interpolation proportional to speed ---
        // At low speed, allow rapid bearing changes (stationary pivoting)
        // At high speed, limit bearing change rate
        val speedRatio = (clampedSpeed / profile.maxSpeedMs).coerceIn(0.0, 1.0)
        val effectiveMaxTurnRate =
            profile.maxTurnRateDegPerSec *
                (1.0 - speedRatio * 0.7) // reduce max turn rate at high speed
        val maxBearingDelta = (effectiveMaxTurnRate * deltaTimeSec).toFloat()
        val clampedBearing = clampBearingDelta(location.bearing, previousBearing, maxBearingDelta)

        // --- Accuracy degradation with speed and jump state ---
        val speedPenalty = (clampedSpeed / 22.2f) * 2f // +2m at ~80km/h
        val jumpPenalty = if (wasJump) location.accuracy * 0.5f else 0f
        val coherentAccuracy =
            (location.accuracy + speedPenalty + jumpPenalty)
                .coerceIn(2f, 30f)

        // --- Altitude coherence: bound variation by profile sigma ---
        val altDelta = location.altitude - previousAltitude
        val maxAltDelta = profile.altitudeSigma * 2.0 * deltaTimeSec // 2σ per second
        val clampedAltitude =
            if (abs(altDelta) > maxAltDelta) {
                previousAltitude + maxAltDelta * if (altDelta > 0) 1.0 else -1.0
            } else {
                location.altitude
            }

        // Update state
        previousSpeed = clampedSpeed
        previousBearing = clampedBearing
        previousAltitude = clampedAltitude

        return location.copy(
            speed = clampedSpeed,
            bearing = clampedBearing,
            accuracy = coherentAccuracy,
            altitude = clampedAltitude,
        )
    }

    /**
     * Reset filter state — for new simulation.
     */
    fun reset() {
        previousSpeed = 0f
        previousBearing = 0f
        previousAltitude = 0.0
        isFirstFrame = true
    }

    /**
     * Clamp a value to not differ from the previous by more than maxDelta.
     */
    private fun clampDelta(
        current: Float,
        previous: Float,
        maxDelta: Float
    ): Float {
        val delta = current - previous
        return if (abs(delta) > maxDelta) {
            previous + maxDelta * if (delta > 0) 1f else -1f
        } else {
            current
        }
    }

    /**
     * Clamp bearing change, handling 0°/360° wraparound.
     */
    private fun clampBearingDelta(
        current: Float,
        previous: Float,
        maxDelta: Float
    ): Float {
        var delta = current - previous
        // Normalize to [-180, 180]
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f

        val clampedDelta =
            if (abs(delta) > maxDelta) {
                maxDelta * if (delta > 0) 1f else -1f
            } else {
                delta
            }

        var result = previous + clampedDelta
        // Normalize to [0, 360)
        while (result < 0f) result += 360f
        while (result >= 360f) result -= 360f
        return result
    }
}
