package com.ghostpin.app.diagnostics

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Segment
import com.ghostpin.core.model.SegmentOverrides
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.validation.TrajectoryValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealismDiagnosticsUseCaseTest {
    private val useCase = RealismDiagnosticsUseCase(TrajectoryValidator())

    @Test
    fun `analyze returns diagnostics for explicit route`() {
        val route =
            Route(
                id = "route-1",
                name = "Commute",
                waypoints =
                    listOf(
                        Waypoint(-23.0, -46.0),
                        Waypoint(-22.9995, -45.9995),
                        Waypoint(-22.9990, -45.9990),
                    ),
            )

        val result =
            useCase.analyze(
                RealismDiagnosticsInput(
                    profile = MovementProfile.CAR,
                    route = route,
                    waypoints = emptyList(),
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                )
            )

        assertTrue(result.isSuccess)
        val diagnostics = result.getOrThrow()
        assertEquals("Current route", diagnostics.routeSource)
        assertEquals(route.name, diagnostics.routeName)
        assertEquals(4, diagnostics.metrics.size)
        assertTrue(diagnostics.sampleCount > 0)
        assertTrue(diagnostics.scorePercent in 0..100)
    }

    @Test
    fun `analyze falls back to current waypoints when no route is loaded`() {
        val result =
            useCase.analyze(
                RealismDiagnosticsInput(
                    profile = MovementProfile.PEDESTRIAN,
                    route = null,
                    waypoints =
                        listOf(
                            Waypoint(-23.0, -46.0),
                            Waypoint(-22.999, -45.999),
                        ),
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -23.0,
                    endLng = -46.0,
                )
            )

        assertTrue(result.isSuccess)
        assertEquals("Current waypoints", result.getOrThrow().routeSource)
    }

    @Test
    fun `analyze surfaces trajectory warnings for impossible segment overrides`() {
        val route =
            Route(
                id = "route-2",
                name = "Unsafe route",
                waypoints =
                    listOf(
                        Waypoint(-23.0, -46.0),
                        Waypoint(-22.999, -45.999),
                    ),
                segments =
                    listOf(
                        Segment(
                            id = "seg-1",
                            fromIndex = 0,
                            toIndex = 1,
                            distance = 1000.0,
                            overrides = SegmentOverrides(speedOverrideMs = 50.0),
                        )
                    ),
            )

        val result =
            useCase.analyze(
                RealismDiagnosticsInput(
                    profile = MovementProfile.PEDESTRIAN,
                    route = route,
                    waypoints = emptyList(),
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                )
            )

        assertTrue(result.isSuccess)
        val diagnostics = result.getOrThrow()
        assertFalse(diagnostics.trajectoryWarnings.isEmpty())
    }

    @Test
    fun `analyze fails when no route context exists`() {
        val result =
            useCase.analyze(
                RealismDiagnosticsInput(
                    profile = MovementProfile.CAR,
                    route = null,
                    waypoints = emptyList(),
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -23.0,
                    endLng = -46.0,
                )
            )

        assertTrue(result.isFailure)
    }
}
