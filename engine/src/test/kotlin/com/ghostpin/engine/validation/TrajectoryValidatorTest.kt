package com.ghostpin.engine.validation

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Segment
import com.ghostpin.core.model.SegmentOverrides
import com.ghostpin.core.model.Waypoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the trajectory validator.
 */
class TrajectoryValidatorTest {
    private val validator = TrajectoryValidator()

    @Test
    fun `valid route produces no warnings`() {
        val route =
            Route(
                id = "test-valid",
                name = "Normal Walk",
                waypoints =
                    listOf(
                        Waypoint(lat = -23.550, lng = -46.633, bearing = 0f),
                        Waypoint(lat = -23.551, lng = -46.633, bearing = 10f),
                        Waypoint(lat = -23.552, lng = -46.634, bearing = 20f),
                    ),
            )

        val result = validator.validate(route, MovementProfile.PEDESTRIAN)
        assertTrue(result.isValid, "Valid route should pass: ${result.warnings}")
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `impossible speed override produces warning`() {
        val route =
            Route(
                id = "test-speed",
                name = "Too Fast Walk",
                waypoints =
                    listOf(
                        Waypoint(lat = 0.0, lng = 0.0),
                        Waypoint(lat = 0.001, lng = 0.0),
                    ),
                segments =
                    listOf(
                        Segment(
                            id = "s1",
                            fromIndex = 0,
                            toIndex = 1,
                            distance = 111.0, // meters
                            overrides = SegmentOverrides(speedOverrideMs = 50.0), // 180 km/h on foot
                        )
                    ),
            )

        val result = validator.validate(route, MovementProfile.PEDESTRIAN)
        assertFalse(result.isValid, "Should fail: pedestrian speed override at 50 m/s")
        assertTrue(result.warnings.any { "speed override" in it })
    }

    @Test
    fun `impossible distance for pause produces warning`() {
        val route =
            Route(
                id = "test-pause",
                name = "Short Pause Long Distance",
                waypoints =
                    listOf(
                        Waypoint(lat = 0.0, lng = 0.0),
                        Waypoint(lat = 0.01, lng = 0.0),
                    ),
                segments =
                    listOf(
                        Segment(
                            id = "s1",
                            fromIndex = 0,
                            toIndex = 1,
                            distance = 1000.0,
                            overrides = SegmentOverrides(pauseDurationSec = 1.0), // 1000m in 1s = 1000 m/s
                        )
                    ),
            )

        val result = validator.validate(route, MovementProfile.PEDESTRIAN)
        assertFalse(result.isValid)
        assertTrue(result.warnings.any { "requires speed" in it })
    }

    @Test
    fun `car can handle higher speeds that pedestrian cannot`() {
        val route =
            Route(
                id = "test-car",
                name = "Highway",
                waypoints =
                    listOf(
                        Waypoint(lat = 0.0, lng = 0.0),
                        Waypoint(lat = 0.001, lng = 0.0),
                    ),
                segments =
                    listOf(
                        Segment(
                            id = "s1",
                            fromIndex = 0,
                            toIndex = 1,
                            distance = 111.0,
                            overrides = SegmentOverrides(speedOverrideMs = 30.0), // 108 km/h
                        )
                    ),
            )

        val pedResult = validator.validate(route, MovementProfile.PEDESTRIAN)
        val carResult = validator.validate(route, MovementProfile.CAR)

        assertFalse(pedResult.isValid, "Pedestrian cannot do 30 m/s")
        assertTrue(carResult.isValid, "Car can do 30 m/s: ${carResult.warnings}")
    }
}
