package com.ghostpin.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for [GeoMath] — haversine distance and bearing calculations.
 *
 * Sprint 9 — Task 33.
 */
class GeoMathTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double, msg: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$msg Expected ~$expected, got $actual (tol=$tolerance)")
    }

    // ── haversineMeters ──────────────────────────────────────────────────────

    @Test
    fun `same point returns zero distance`() {
        val d = GeoMath.haversineMeters(-23.5505, -46.6333, -23.5505, -46.6333)
        assertApprox(0.0, d, 1e-6, "Same point")
    }

    @Test
    fun `known distance SP to RJ is approximately 358 km`() {
        val d = GeoMath.haversineMeters(-23.5505, -46.6333, -22.9068, -43.1729)
        assertTrue(d in 350_000.0..370_000.0, "Expected ~358 km, got ${d / 1000} km")
    }

    @Test
    fun `short distance within city block is accurate`() {
        val d = GeoMath.haversineMeters(0.0, 0.0, 0.001, 0.0)
        assertTrue(d in 100.0..120.0, "Expected ~111m, got ${d}m")
    }

    @Test
    fun `haversine across antimeridian works`() {
        val d = GeoMath.haversineMeters(0.0, 179.0, 0.0, -179.0)
        assertTrue(d in 200_000.0..250_000.0, "Expected ~222 km, got ${d / 1000} km")
    }

    @Test
    fun `poles to equator distance is approximately quarter circumference`() {
        val d = GeoMath.haversineMeters(90.0, 0.0, 0.0, 0.0)
        assertTrue(d in 9_900_000.0..10_100_000.0, "Expected ~10007 km, got ${d / 1000} km")
    }

    // ── bearingBetween ───────────────────────────────────────────────────────

    @Test
    fun `bearing due North is 0 degrees`() {
        val b = GeoMath.bearingBetween(0.0, 0.0, 1.0, 0.0)
        assertApprox(0.0, b.toDouble(), 0.5, "North")
    }

    @Test
    fun `bearing due East is 90 degrees`() {
        val b = GeoMath.bearingBetween(0.0, 0.0, 0.0, 1.0)
        assertApprox(90.0, b.toDouble(), 0.5, "East")
    }

    @Test
    fun `bearing due South is 180 degrees`() {
        val b = GeoMath.bearingBetween(1.0, 0.0, 0.0, 0.0)
        assertApprox(180.0, b.toDouble(), 0.5, "South")
    }

    @Test
    fun `bearing due West is 270 degrees`() {
        val b = GeoMath.bearingBetween(0.0, 1.0, 0.0, 0.0)
        assertApprox(270.0, b.toDouble(), 0.5, "West")
    }

    @Test
    fun `bearing is in range 0 to 360`() {
        val b = GeoMath.bearingBetween(-23.5505, -46.6333, -22.9068, -43.1729)
        assertTrue(b >= 0f && b < 360f, "Bearing $b should be in [0, 360)")
    }
}
