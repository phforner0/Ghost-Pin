package com.ghostpin.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [Route] extension properties and utility functions.
 *
 * Sprint 9 — Task 33.
 */
class RouteExtensionsTest {
    private fun assertApprox(
        expected: Double,
        actual: Double,
        tolerance: Double,
        msg: String = ""
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "$msg Expected ~$expected, got $actual (tol=$tolerance)")
    }

    // ── Test helpers ─────────────────────────────────────────────────────────

    private fun route(
        vararg coords: Pair<Double, Double>,
        segments: List<Segment> = emptyList()
    ) = Route(
        id = "test-route",
        name = "Test Route",
        waypoints = coords.map { (lat, lng) -> Waypoint(lat, lng) },
        segments = segments,
    )

    private val walkProfile = MovementProfile.PEDESTRIAN
    private val carProfile = MovementProfile.CAR

    // ── distanceMeters ───────────────────────────────────────────────────────

    @Test
    fun `distanceMeters from segments uses segment distances`() {
        val r =
            route(
                0.0 to 0.0,
                0.01 to 0.0,
                segments =
                    listOf(
                        Segment(id = "s1", fromIndex = 0, toIndex = 1, distance = 500.0),
                    ),
            )
        assertApprox(500.0, r.distanceMeters, 0.01)
    }

    @Test
    fun `distanceMeters sums multiple segments`() {
        val r =
            route(
                0.0 to 0.0,
                0.01 to 0.0,
                0.02 to 0.0,
                segments =
                    listOf(
                        Segment(id = "s1", fromIndex = 0, toIndex = 1, distance = 500.0),
                        Segment(id = "s2", fromIndex = 1, toIndex = 2, distance = 700.0),
                    ),
            )
        assertApprox(1200.0, r.distanceMeters, 0.01)
    }

    @Test
    fun `distanceMeters falls back to haversine when no segments`() {
        val r = route(0.0 to 0.0, 0.01 to 0.0)
        assertTrue(r.distanceMeters in 1000.0..1200.0, "Expected ~1111m, got ${r.distanceMeters}m")
    }

    // ── estimateDuration ────────────────────────────────────────────────────

    @Test
    fun `estimateDuration uses cruise speed (80 percent of max)`() {
        val r =
            route(
                0.0 to 0.0,
                0.01 to 0.0,
                segments =
                    listOf(
                        Segment(id = "s1", fromIndex = 0, toIndex = 1, distance = 1000.0),
                    ),
            )
        val expected = 1000.0 / (walkProfile.maxSpeedMs * 0.8)
        val duration = r.estimateDuration(walkProfile)
        assertApprox(expected, duration, 1.0, "Walk duration")
    }

    @Test
    fun `estimateDuration respects segment speed override`() {
        val r =
            route(
                0.0 to 0.0,
                0.01 to 0.0,
                segments =
                    listOf(
                        Segment(
                            id = "s1",
                            fromIndex = 0,
                            toIndex = 1,
                            distance = 1000.0,
                            overrides = SegmentOverrides(speedOverrideMs = 2.0),
                        ),
                    ),
            )
        val duration = r.estimateDuration(walkProfile)
        assertApprox(500.0, duration, 0.01, "Override speed 2.0 m/s")
    }

    @Test
    fun `estimateDuration fallback when no segments`() {
        val r = route(0.0 to 0.0, 0.01 to 0.0)
        val duration = r.estimateDuration(carProfile)
        val expected = r.distanceMeters / (carProfile.maxSpeedMs * 0.8)
        assertApprox(expected, duration, 1.0, "Car fallback")
    }

    // ── formatDuration ──────────────────────────────────────────────────────

    @Test
    fun `formatDuration formats seconds only`() {
        assertEquals("45s", formatDuration(45.0))
    }

    @Test
    fun `formatDuration formats minutes and seconds`() {
        assertEquals("1m 15s", formatDuration(75.0))
    }

    @Test
    fun `formatDuration formats hours and minutes`() {
        assertEquals("1h 2m", formatDuration(3720.0))
    }

    @Test
    fun `formatDuration handles zero`() {
        assertEquals("0s", formatDuration(0.0))
    }

    @Test
    fun `formatDuration handles negative input`() {
        assertEquals("0s", formatDuration(-10.0))
    }

    @Test
    fun `formatDuration handles exact hour`() {
        assertEquals("1h", formatDuration(3600.0))
    }

    @Test
    fun `formatDuration handles exact minute`() {
        assertEquals("1m", formatDuration(60.0))
    }

    // ── durationSeconds legacy stub ─────────────────────────────────────────

    @Test
    fun `durationSeconds always returns zero (legacy stub)`() {
        val r = route(0.0 to 0.0, 0.01 to 0.0)
        assertApprox(0.0, r.durationSeconds, 0.0)
    }

    // ── Route init ──────────────────────────────────────────────────────────

    @Test
    fun `route requires at least 2 waypoints`() {
        assertFailsWith<IllegalArgumentException> {
            Route(
                id = "bad",
                name = "Bad",
                waypoints = listOf(Waypoint(0.0, 0.0)),
            )
        }
    }
}
