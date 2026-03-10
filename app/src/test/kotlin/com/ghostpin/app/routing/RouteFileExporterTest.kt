package com.ghostpin.app.routing

import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [RouteFileExporter].
 *
 * Sprint 4 — Task 16.
 *
 * Validates that exported files contain the expected coordinate values
 * and are parseable back by [RouteFileParser] (round-trip test).
 */
class RouteFileExporterTest {

    private val exporter = RouteFileExporter()
    private val parser = RouteFileParser()

    private val testRoute = Route(
        id = "test-export",
        name = "SP Test Route",
        waypoints = listOf(
            Waypoint(lat = -23.5505, lng = -46.6333, altitude = 760.0),
            Waypoint(lat = -23.5520, lng = -46.6350, altitude = 761.5),
            Waypoint(lat = -23.5540, lng = -46.6370, altitude = 762.0),
        ),
    )

    // ── GPX ──────────────────────────────────────────────────────────────────

    @Test
    fun `GPX output contains route name`() {
        val gpx = exporter.toGpx(testRoute)
        assertTrue(gpx.contains("SP Test Route"))
    }

    @Test
    fun `GPX output contains all waypoint coordinates`() {
        val gpx = exporter.toGpx(testRoute)
        assertTrue(gpx.contains("-23.5505000"))
        assertTrue(gpx.contains("-46.6333000"))
        assertTrue(gpx.contains("760.0000000"))
    }

    @Test
    fun `GPX round-trip preserves waypoint count`() {
        val gpx = exporter.toGpx(testRoute)
        val parsed = parser.parse(gpx).getOrThrow()
        assertEquals(testRoute.waypoints.size, parsed.waypoints.size)
    }

    @Test
    fun `GPX round-trip preserves coordinates within tolerance`() {
        val gpx = exporter.toGpx(testRoute)
        val parsed = parser.parse(gpx).getOrThrow()
        testRoute.waypoints.zip(parsed.waypoints).forEach { (orig, parsed) ->
            assertEquals(orig.lat, parsed.lat, 1e-5)
            assertEquals(orig.lng, parsed.lng, 1e-5)
        }
    }

    // ── KML ──────────────────────────────────────────────────────────────────

    @Test
    fun `KML output contains route name`() {
        val kml = exporter.toKml(testRoute)
        assertTrue(kml.contains("SP Test Route"))
    }

    @Test
    fun `KML uses lng,lat,alt coordinate order`() {
        val kml = exporter.toKml(testRoute)
        // First waypoint: lng=-46.6333, lat=-23.5505
        assertTrue(kml.contains("-46.6333000,-23.5505000"))
    }

    @Test
    fun `KML round-trip preserves waypoint count`() {
        val kml = exporter.toKml(testRoute)
        val parsed = parser.parse(kml).getOrThrow()
        assertEquals(testRoute.waypoints.size, parsed.waypoints.size)
    }

    // ── TCX ──────────────────────────────────────────────────────────────────

    @Test
    fun `TCX output contains correct latitude and longitude tags`() {
        val tcx = exporter.toTcx(testRoute)
        assertTrue(tcx.contains("<LatitudeDegrees>-23.5505000</LatitudeDegrees>"))
        assertTrue(tcx.contains("<LongitudeDegrees>-46.6333000</LongitudeDegrees>"))
    }

    @Test
    fun `TCX round-trip preserves waypoint count`() {
        val tcx = exporter.toTcx(testRoute)
        val parsed = parser.parse(tcx).getOrThrow()
        assertEquals(testRoute.waypoints.size, parsed.waypoints.size)
    }

    // ── XML escaping ──────────────────────────────────────────────────────────

    @Test
    fun `GPX escapes special characters in route name`() {
        val routeWithSpecialChars = testRoute.copy(name = "Route <Test> & 'More'")
        val gpx = exporter.toGpx(routeWithSpecialChars)
        assertTrue(gpx.contains("Route &lt;Test&gt; &amp; &apos;More&apos;"))
        // Should NOT contain raw < or & in the name tag
        assertFalse(gpx.contains("<n>Route <Test>"))
    }
}
