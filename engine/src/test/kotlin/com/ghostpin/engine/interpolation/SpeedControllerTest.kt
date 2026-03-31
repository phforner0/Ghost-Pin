package com.ghostpin.engine.interpolation

import com.ghostpin.core.model.MovementProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpeedControllerTest {
    private val pedestrian = MovementProfile.PEDESTRIAN // maxSpeedMs=2.0, maxAccelMs2=1.5
    private val car = MovementProfile.CAR // maxSpeedMs=33.3, maxAccelMs2=4.0
    private val dt = 1.0 // 1-second frame

    // ── Acceleration from rest ─────────────────────────────────────────────

    @Test
    fun `speed starts at zero`() {
        val ctrl = SpeedController(pedestrian)
        assertEquals(0.0, ctrl.currentSpeedMs, 1e-9)
    }

    @Test
    fun `speed increases from zero after first advance`() {
        val ctrl = SpeedController(pedestrian)
        ctrl.advance(dt)
        assertTrue(ctrl.currentSpeedMs > 0.0)
    }

    @Test
    fun `speed never exceeds profile max`() {
        val ctrl = SpeedController(car, initialRatio = 1.0)
        repeat(100) { ctrl.advance(dt) }
        assertTrue(
            ctrl.currentSpeedMs <= car.maxSpeedMs + 1e-9,
            "Speed ${ctrl.currentSpeedMs} exceeds max ${car.maxSpeedMs}"
        )
    }

    // ── Acceleration clamping ─────────────────────────────────────────────

    @Test
    fun `speed delta per frame never exceeds maxAccel * dt`() {
        val ctrl = SpeedController(pedestrian, initialRatio = 1.0)
        var prevSpeed = ctrl.currentSpeedMs
        for (i in 0..50) {
            ctrl.advance(dt)
            val delta = kotlin.math.abs(ctrl.currentSpeedMs - prevSpeed)
            assertTrue(
                delta <= pedestrian.maxAccelMs2 * dt + 1e-9,
                "Frame $i speed delta $delta > maxAccel*dt ${pedestrian.maxAccelMs2 * dt}"
            )
            prevSpeed = ctrl.currentSpeedMs
        }
    }

    @Test
    fun `deceleration also respects maxAccel constraint`() {
        val ctrl = SpeedController(pedestrian, initialRatio = 1.0)
        // Ramp up first
        repeat(20) { ctrl.advance(dt) }
        val topSpeed = ctrl.currentSpeedMs
        // Now brake
        ctrl.targetRatio = 0.0
        val prevSpeed = ctrl.currentSpeedMs
        ctrl.advance(dt)
        val delta = prevSpeed - ctrl.currentSpeedMs
        assertTrue(
            delta <= pedestrian.maxAccelMs2 * dt + 1e-9,
            "Deceleration $delta exceeds maxAccel*dt"
        )
        assertTrue(ctrl.currentSpeedMs >= 0.0, "Speed went negative")
    }

    // ── Waypoint slowdown ─────────────────────────────────────────────────

    @Test
    fun `speed is lower near waypoint than in open stretch`() {
        val ctrl = SpeedController(car, initialRatio = 1.0)
        // Bring controller up to cruise speed
        repeat(30) { ctrl.advance(dt) }
        val cruiseSpeed = ctrl.currentSpeedMs

        // Simulate approaching a waypoint (5m away)
        ctrl.reset()
        repeat(30) { ctrl.advance(dt, distToNextWaypoint = 5.0) }
        val waypointSpeed = ctrl.currentSpeedMs

        assertTrue(
            waypointSpeed < cruiseSpeed,
            "Expected slowdown near waypoint: waypointSpeed=$waypointSpeed cruiseSpeed=$cruiseSpeed"
        )
    }

    @Test
    fun `no slowdown far from waypoint`() {
        val ctrl = SpeedController(car, initialRatio = 0.5)
        repeat(30) { ctrl.advance(dt, distToNextWaypoint = Double.MAX_VALUE) }
        val farSpeed = ctrl.currentSpeedMs
        ctrl.reset()
        repeat(30) { ctrl.advance(dt, distToNextWaypoint = 100.0) }
        val mediumSpeed = ctrl.currentSpeedMs
        assertEquals(farSpeed, mediumSpeed, 1e-6)
    }

    // ── targetRatio ───────────────────────────────────────────────────────

    @Test
    fun `targetRatio 0 causes full stop over time`() {
        val ctrl = SpeedController(pedestrian, initialRatio = 1.0)
        repeat(10) { ctrl.advance(dt) } // reach speed
        ctrl.targetRatio = 0.0
        repeat(20) { ctrl.advance(dt) } // brake to stop
        assertEquals(0.0, ctrl.currentSpeedMs, 1e-9)
    }

    @Test
    fun `targetRatio clamps to 1`() {
        val ctrl = SpeedController(car)
        ctrl.targetRatio = 99.0
        assertEquals(1.0, ctrl.targetRatio, 1e-9)
    }

    @Test
    fun `targetRatio clamps to 0`() {
        val ctrl = SpeedController(car)
        ctrl.targetRatio = -5.0
        assertEquals(0.0, ctrl.targetRatio, 1e-9)
    }

    // ── advance returns positive distance ────────────────────────────────

    @Test
    fun `advance returns non-negative distance`() {
        val ctrl = SpeedController(pedestrian)
        val dist = ctrl.advance(dt)
        assertTrue(dist >= 0.0)
    }

    @Test
    fun `advance returns distance consistent with current speed`() {
        val ctrl = SpeedController(pedestrian, initialRatio = 1.0)
        repeat(20) { ctrl.advance(dt) }
        val dist = ctrl.advance(dt)
        assertEquals(ctrl.currentSpeedMs * dt, dist, 1e-9)
    }

    // ── reset ─────────────────────────────────────────────────────────────

    @Test
    fun `reset sets speed back to zero`() {
        val ctrl = SpeedController(pedestrian)
        repeat(10) { ctrl.advance(dt) }
        ctrl.reset()
        assertEquals(0.0, ctrl.currentSpeedMs, 1e-9)
    }

    // ── estimateDurationSec ───────────────────────────────────────────────

    @Test
    fun `estimateDurationSec returns distance divided by cruise speed`() {
        val ctrl = SpeedController(car, initialRatio = 0.5)
        val expected = 1000.0 / (car.maxSpeedMs * 0.5)
        assertEquals(expected, ctrl.estimateDurationSec(1000.0), 1e-6)
    }
}
