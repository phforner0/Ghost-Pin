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
    fun `bearing is always in range 0 to 360`() {
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
        // Asymmetric S-curve: tangent contributions at p0/p3 don't cancel
        val curved = Route(
            id = "curved",
            name = "S-curve",
            waypoints = listOf(
                Waypoint(lat = 0.0,   lng = 0.0),    // p0: start
                Waypoint(lat = 0.0,   lng = 0.02),   // p1: east  (→)
                Waypoint(lat = 0.01,  lng = 0.02),   // p2: north (↑)
                Waypoint(lat = 0.02,  lng = 0.0),    // p3: south-west (↙) — asymmetric!
            ),
        )

        val interp = RouteInterpolator(curved)

        // Sample the Catmull-Rom at 25% of segment 1 (p1→p2) — away from midpoint
        // to avoid symmetric tangent cancellation at t=0.5.
        val seg1Start = interp.distanceToWaypoint(1)
        val seg1End   = interp.distanceToWaypoint(2)
        val sampleDist = seg1Start + (seg1End - seg1Start) * 0.25

        val crPos = interp.positionAt(sampleDist)

        // Corresponding linear 25% along the same segment
        val p1 = curved.waypoints[1]
        val p2 = curved.waypoints[2]
        val linLat = p1.lat + (p2.lat - p1.lat) * 0.25
        val linLng = p1.lng + (p2.lng - p1.lng) * 0.25

        // The CR spline should differ from linear because incoming tangent
        // (eastward from seg 0) and outgoing tangent (south-west from seg 2)
        // pull the curve away from the straight line.
        val latDiff = abs(crPos.lat - linLat)
        val lngDiff = abs(crPos.lng - linLng)
        assertTrue(latDiff > 1e-6 || lngDiff > 1e-6,
            "Catmull-Rom and linear should differ for asymmetric S-curve: " +
            "cr=(${crPos.lat}, ${crPos.lng}) lin=($linLat, $linLng)")
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
