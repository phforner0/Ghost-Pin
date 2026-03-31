package com.ghostpin.app.service

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Segment
import com.ghostpin.core.model.SegmentOverrides
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.model.distanceMeters
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationRuntimeOverridesTest {
    private val route =
        Route(
            id = "route-1",
            name = "Route",
            waypoints =
                listOf(
                    Waypoint(0.0, 0.0, altitude = 10.0),
                    Waypoint(0.0, 0.001, altitude = 20.0),
                    Waypoint(0.0, 0.002, altitude = 30.0),
                ),
            segments =
                listOf(
                    Segment(
                        id = "s0",
                        fromIndex = 0,
                        toIndex = 1,
                        distance = 100.0,
                        overrides = SegmentOverrides(speedOverrideMs = 12.0, pauseDurationSec = 3.0, loop = true),
                    ),
                    Segment(
                        id = "s1",
                        fromIndex = 1,
                        toIndex = 2,
                        distance = 150.0,
                        overrides = SegmentOverrides(speedOverrideMs = 6.0, pauseDurationSec = 1.0, loop = false),
                    ),
                ),
        )

    @Test
    fun `segment loop override is one-shot and disabled under repeat policy`() {
        val first =
            resolveSegmentRuntimeBehavior(
                route = route,
                segmentIndex = 0,
                profile = MovementProfile.CAR,
                baseSpeedRatio = 1.0,
                defaultWaypointPauseSec = 0.0,
                repeatPolicy = RepeatPolicy.NONE,
                triggeredLoopSegments = emptySet(),
            )
        val afterTrigger =
            resolveSegmentRuntimeBehavior(
                route = route,
                segmentIndex = 0,
                profile = MovementProfile.CAR,
                baseSpeedRatio = 1.0,
                defaultWaypointPauseSec = 0.0,
                repeatPolicy = RepeatPolicy.NONE,
                triggeredLoopSegments = setOf(0),
            )
        val withGlobalRepeat =
            resolveSegmentRuntimeBehavior(
                route = route,
                segmentIndex = 0,
                profile = MovementProfile.CAR,
                baseSpeedRatio = 1.0,
                defaultWaypointPauseSec = 0.0,
                repeatPolicy = RepeatPolicy.LOOP_N,
                triggeredLoopSegments = emptySet(),
            )

        assertTrue(first.shouldRestartFromStart)
        assertFalse(afterTrigger.shouldRestartFromStart)
        assertFalse(withGlobalRepeat.shouldRestartFromStart)
    }

    @Test
    fun `segment runtime behavior respects speed and pause overrides`() {
        val behavior =
            resolveSegmentRuntimeBehavior(
                route = route,
                segmentIndex = 1,
                profile = MovementProfile.CAR,
                baseSpeedRatio = 1.0,
                defaultWaypointPauseSec = 5.0,
                repeatPolicy = RepeatPolicy.NONE,
                triggeredLoopSegments = emptySet(),
            )

        assertEquals(6.0 / MovementProfile.CAR.maxSpeedMs, behavior.targetRatio, 1e-9)
        assertEquals(1.0, behavior.pauseSec, 0.0)
        assertFalse(behavior.shouldRestartFromStart)
    }

    @Test
    fun `estimate runtime duration uses segment overrides and pauses`() {
        val durationSec =
            estimateRuntimeDurationSec(
                route = route,
                profile = MovementProfile.CAR,
                baseSpeedRatio = 1.0,
                defaultWaypointPauseSec = 0.0,
            )

        val expected = (100.0 / 12.0) + 3.0 + (150.0 / 6.0) + 1.0
        assertEquals(expected, durationSec, 1e-9)
    }

    @Test
    fun `estimate runtime duration falls back to base speed when route has no segments`() {
        val fallbackRoute =
            Route(
                id = "route-2",
                name = "Fallback",
                waypoints =
                    listOf(
                        Waypoint(0.0, 0.0),
                        Waypoint(0.0, 0.001),
                    ),
            )

        val durationSec =
            estimateRuntimeDurationSec(
                route = fallbackRoute,
                profile = MovementProfile.PEDESTRIAN,
                baseSpeedRatio = 0.5,
                defaultWaypointPauseSec = 2.0,
            )

        val expected = (fallbackRoute.distanceMeters / (MovementProfile.PEDESTRIAN.maxSpeedMs * 0.5)) + 2.0
        assertEquals(expected, durationSec, 1e-9)
    }
}
