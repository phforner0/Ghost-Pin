package com.ghostpin.engine.noise

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the sensor coherence filter — validates Gap 3 mitigation.
 */
class SensorCoherenceTest {

    private val profile = MovementProfile.CAR

    @Test
    fun `speed never jumps more than maxAccel times deltaT`() {
        val filter = SensorCoherenceFilter(profile)
        val dt = 1.0

        // Start with speed 0, then try to jump to 100 m/s
        val loc1 = MockLocation(lat = 0.0, lng = 0.0, speed = 0f, bearing = 0f)
        val result1 = filter.apply(loc1, dt, wasJump = false)

        val loc2 = MockLocation(lat = 0.0, lng = 0.0, speed = 100f, bearing = 0f)
        val result2 = filter.apply(loc2, dt, wasJump = false)

        val speedDelta = kotlin.math.abs(result2.speed - result1.speed)
        val maxDelta = (profile.maxAccelMs2 * dt).toFloat()

        assertTrue(
            speedDelta <= maxDelta + 0.01f, // small epsilon for floating point
            "Speed delta ($speedDelta) exceeds max ($maxDelta)"
        )
    }

    @Test
    fun `speed is always non-negative`() {
        val filter = SensorCoherenceFilter(profile)

        val loc1 = MockLocation(lat = 0.0, lng = 0.0, speed = 5f)
        filter.apply(loc1, 1.0, wasJump = false)

        val loc2 = MockLocation(lat = 0.0, lng = 0.0, speed = -10f)
        val result = filter.apply(loc2, 1.0, wasJump = false)

        assertTrue(result.speed >= 0f, "Speed should never be negative, got ${result.speed}")
    }

    @Test
    fun `bearing never flips 180 degrees instantaneously at high speed`() {
        val filter = SensorCoherenceFilter(profile)
        val dt = 1.0

        val loc1 = MockLocation(lat = 0.0, lng = 0.0, speed = 30f, bearing = 0f)
        filter.apply(loc1, dt, wasJump = false)

        // Try to flip bearing by 180° at high speed
        val loc2 = MockLocation(lat = 0.0, lng = 0.0, speed = 30f, bearing = 180f)
        val result = filter.apply(loc2, dt, wasJump = false)

        // The effective max turn rate at high speed should prevent this
        val bearingDelta = kotlin.math.abs(result.bearing - 0f)
        val effectiveMaxTurn = profile.maxTurnRateDegPerSec *
            (1.0 - (30.0 / profile.maxSpeedMs) * 0.7)
        val maxBearing = effectiveMaxTurn * dt

        assertTrue(
            bearingDelta <= maxBearing + 1.0, // tolerance
            "Bearing change ($bearingDelta°) exceeds max turn rate ($maxBearing°)"
        )
    }

    @Test
    fun `accuracy increases with speed`() {
        val filter = SensorCoherenceFilter(profile)

        val locSlow = MockLocation(lat = 0.0, lng = 0.0, speed = 0f, accuracy = 5f)
        filter.apply(locSlow, 1.0, wasJump = false)

        // Reset for clean comparison
        val filter2 = SensorCoherenceFilter(profile)

        val locFast = MockLocation(lat = 0.0, lng = 0.0, speed = 25f, accuracy = 5f)
        val resultFast = filter2.apply(locFast, 1.0, wasJump = false)

        // First frame is returned as-is, so test on second frame
        val locFast2 = MockLocation(lat = 0.0, lng = 0.0, speed = 25f, accuracy = 5f)
        val resultFast2 = filter2.apply(locFast2, 1.0, wasJump = false)

        assertTrue(
            resultFast2.accuracy > 5f,
            "Accuracy at high speed should be worse (higher) than base 5m, got ${resultFast2.accuracy}"
        )
    }

    @Test
    fun `multipath jump increases accuracy penalty`() {
        val filter = SensorCoherenceFilter(profile)

        val loc1 = MockLocation(lat = 0.0, lng = 0.0, speed = 5f, accuracy = 8f)
        filter.apply(loc1, 1.0, wasJump = false)

        // Without jump
        val locNoJump = MockLocation(lat = 0.0, lng = 0.0, speed = 5f, accuracy = 8f)
        val noJumpResult = filter.apply(locNoJump, 1.0, wasJump = false)

        val filter2 = SensorCoherenceFilter(profile)
        val loc2 = MockLocation(lat = 0.0, lng = 0.0, speed = 5f, accuracy = 8f)
        filter2.apply(loc2, 1.0, wasJump = false)

        // With jump
        val locJump = MockLocation(lat = 0.0, lng = 0.0, speed = 5f, accuracy = 8f)
        val jumpResult = filter2.apply(locJump, 1.0, wasJump = true)

        assertTrue(
            jumpResult.accuracy > noJumpResult.accuracy,
            "Accuracy during jump (${jumpResult.accuracy}) should be worse than no-jump (${noJumpResult.accuracy})"
        )
    }

    @Test
    fun `reset restores initial state`() {
        val filter = SensorCoherenceFilter(profile)
        val loc = MockLocation(lat = 0.0, lng = 0.0, speed = 10f, bearing = 90f)
        filter.apply(loc, 1.0, wasJump = false)
        filter.reset()

        // After reset, next apply should treat it as first frame
        val loc2 = MockLocation(lat = 0.0, lng = 0.0, speed = 50f, bearing = 270f)
        val result = filter.apply(loc2, 1.0, wasJump = false)
        // First frame is returned unchanged
        assertEquals(50f, result.speed)
    }
}
