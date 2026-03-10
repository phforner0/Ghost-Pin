package com.ghostpin.engine.interpolation

import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RouteInterpolatorTest {

    // São Paulo downtown waypoints (~600m route)
    private val twoPointRoute = Route(
        id = "test-2pt",
        name = "Two-point linear",
        waypoints = listOf(
            Waypoint(lat = -23.5505, lng = -46.6333),
            Waypoint(lat = -23.5560, lng = -46.6400),
        ),
    )

    private val fourPointRoute = Route(
        id = "test-4pt",
        name = "Four-point Catmull-Rom",
        waypoints = listOf(
            Waypoint(lat = -23.5505, lng = -46.6333),
            Waypoint(lat = -23.5520, lng = -46.6350),
            Waypoint(lat = -23.5540, lng = -46.6370),
            Waypoint(lat = -23.5560, lng = -46.6400),
        ),
    )

    // ── positionAt boundary conditions ────────────────────────────────────

    @Test
    fun `positionAt 0 returns start waypoint`() {
        val interp = RouteInterpolator(twoPointRoute)
        val frame  = interp.positionAt(0.0)
        assertEquals(-23.5505, frame.lat, 1e-6)
        assertEquals(-46.6333, frame.lng, 1e-6)
        assertEquals(0.0, frame.progress, 1e-9)
    }

    @Test
    fun `positionAt totalDistance returns end waypoint`() {
        val interp = RouteInterpolator(twoPointRoute)
        val frame  = interp.positionAt(interp.totalDistanceMeters)
        assertEquals(-23.5560, frame.lat, 1e-5)
        assertEquals(-46.6400, frame.lng, 1e-5)
        assertEquals(1.0, frame.progress, 1e-9)
    }

    @Test
    fun `positionAt beyond totalDistance clamps to end`() {
        val interp = RouteInterpolator(twoPointRoute)
        val frame  = interp.positionAt(interp.totalDistanceMeters + 9999.0)
        assertEquals(1.0, frame.progress, 1e-9)
    }

    @Test
    fun `positionAt negative distance clamps to start`() {
        val interp = RouteInterpolator(twoPointRoute)
        val frame  = interp.positionAt(-100.0)
        assertEquals(0.0, frame.progress, 1e-9)
    }

    // ── progress is monotonic ─────────────────────────────────────────────

    @Test
    fun `progress increases monotonically along route`() {
        val interp  = RouteInterpolator(fourPointRoute)
        val total   = interp.totalDistanceMeters
        val samples = 200
        var prevProgress = -1.0
        for (i in 0..samples) {
            val dist  = (i.toDouble() / samples) * total
            val frame = interp.positionAt(dist)
            assertTrue(frame.progress >= prevProgress - 1e-10,
                "Progress went backwards at dist=$dist: ${frame.progress} < $prevProgress")
            prevProgress = frame.progress
        }
    }

    // ── totalDistanceMeters is positive ──────────────────────────────────

    @Test
    fun `total distance is positive for non-degenerate route`() {
        val interp = RouteInterpolator(twoPointRoute)
        assertTrue(interp.totalDistanceMeters > 0.0)
    }

    @Test
    fun `total distance matches sum of haversine segments`() {
        val wps    = fourPointRoute.waypoints
        val manual = (0 until wps.size - 1).sumOf { i ->
            RouteInterpolator.haversineMeters(wps[i].lat, wps[i].lng, wps[i+1].lat, wps[i+1].lng)
        }
        val interp = RouteInterpolator(fourPointRoute)
        assertEquals(manual, interp.totalDistanceMeters, 1e-6)
    }

    // ── bearing ───────────────────────────────────────────────────────────

    @Test
    fun `bearing at start points roughly south-west for SP test route`() {
        val interp  = RouteInterpolator(twoPointRoute)
        val bearing = interp.positionAt(0.0).bearing
        // Route goes south (lat decreases) and west (lng decreases) → ~225°
        assertTrue(bearing in 200f..260f, "Unexpected bearing $bearing for SW route")
    }

    @Test
    fun `bearing is always in [0, 360)`() {
        val interp  = RouteInterpolator(fourPointRoute)
        val total   = interp.totalDistanceMeters
        for (i in 0..100) {
            val b = interp.positionAt(i.toDouble() / 100.0 * total).bearing
            assertTrue(b in 0f..<360f, "Bearing out of range: $b")
        }
    }

    // ── Catmull-Rom vs Linear midpoint ───────────────────────────────────

    @Test
    fun `catmull-rom midpoint differs from linear midpoint for curved route`() {
        // Build a clearly curved route (90-degree turn)
        val curved = Route(
            id = "curved",
            name = "90-degree turn",
            waypoints = listOf(
                Waypoint(lat = 0.0, lng = 0.0),
                Waypoint(lat = 0.0, lng = 0.01),   // go east
                Waypoint(lat = 0.01, lng = 0.01),  // turn north
                Waypoint(lat = 0.01, lng = 0.02),  // continue east
            ),
        )
        val straight = Route(
            id = "straight",
            name = "Straight line",
            waypoints = listOf(
                Waypoint(lat = 0.0,  lng = 0.0),
                Waypoint(lat = 0.01, lng = 0.02),
            ),
        )

        val crInterp  = RouteInterpolator(curved)
        val linInterp = RouteInterpolator(straight)

        // At 50% progress the CR spline should deviate from the diagonal
        val crMid  = crInterp.positionAt(crInterp.totalDistanceMeters * 0.5)
        val linMid = linInterp.positionAt(linInterp.totalDistanceMeters * 0.5)

        val latDiff = abs(crMid.lat - linMid.lat)
        val lngDiff = abs(crMid.lng - linMid.lng)
        // For this L-shaped route the midpoint should clearly differ
        assertTrue(latDiff > 1e-5 || lngDiff > 1e-5,
            "Catmull-Rom and linear midpoints are suspiciously identical")
    }

    // ── haversineMeters ───────────────────────────────────────────────────

    @Test
    fun `haversine returns ~111km per degree of latitude`() {
        val dist = RouteInterpolator.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_320.0, dist, 500.0)  // ±500m tolerance
    }

    @Test
    fun `haversine returns 0 for same point`() {
        val dist = RouteInterpolator.haversineMeters(-23.55, -46.63, -23.55, -46.63)
        assertEquals(0.0, dist, 1e-9)
    }

    // ── bearingBetween ────────────────────────────────────────────────────

    @Test
    fun `bearing north is 0`() {
        val b = RouteInterpolator.bearingBetween(0.0, 0.0, 1.0, 0.0)
        assertEquals(0f, b, 1f)
    }

    @Test
    fun `bearing east is 90`() {
        val b = RouteInterpolator.bearingBetween(0.0, 0.0, 0.0, 1.0)
        assertEquals(90f, b, 1f)
    }

    @Test
    fun `bearing south is 180`() {
        val b = RouteInterpolator.bearingBetween(1.0, 0.0, 0.0, 0.0)
        assertEquals(180f, b, 1f)
    }

    @Test
    fun `bearing west is 270`() {
        val b = RouteInterpolator.bearingBetween(0.0, 1.0, 0.0, 0.0)
        assertEquals(270f, b, 1f)
    }

    // ── constructor validation ────────────────────────────────────────────

    @Test
    fun `constructor throws for single waypoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteInterpolator(Route("x", "x", listOf(Waypoint(0.0, 0.0))))
        }
    }

    // ── dense sampling doesn't produce NaN ───────────────────────────────

    @Test
    fun `no NaN values from dense sampling of four-point route`() {
        val interp = RouteInterpolator(fourPointRoute)
        val total  = interp.totalDistanceMeters
        for (i in 0..1000) {
            val frame = interp.positionAt(i.toDouble() / 1000.0 * total)
            assertFalse(frame.lat.isNaN(), "lat is NaN at i=$i")
            assertFalse(frame.lng.isNaN(), "lng is NaN at i=$i")
            assertFalse(frame.bearing.isNaN(), "bearing is NaN at i=$i")
        }
    }
}
