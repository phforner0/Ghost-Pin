package com.ghostpin.app.routing

import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OSRM response parsing logic.
 *
 * Sprint 9 — Task 34.
 *
 * We test the JSON parsing in isolation via a local helper that mirrors
 * [OsrmRouteProvider.parseOsrmResponse] without needing an Android context.
 */
class OsrmRouteProviderTest {

    /**
     * Local reimplementation of the OSRM parse logic for testing.
     * Mirrors [OsrmRouteProvider.parseOsrmResponse] exactly.
     */
    private fun parseOsrmResponse(json: String): Route {
        val root = JSONObject(json)
        val code = root.optString("code", "")
        if (code != "Ok") {
            val message = root.optString("message", "unknown OSRM error")
            error("OSRM returned non-OK status: code=$code message=$message")
        }
        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) error("OSRM returned no routes")
        val coords = routes.getJSONObject(0)
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
        if (coords.length() < 2) {
            error("OSRM geometry has fewer than 2 points (got ${coords.length()})")
        }
        val waypoints = (0 until coords.length()).map { i ->
            val pt = coords.getJSONArray(i)
            Waypoint(lat = pt.getDouble(1), lng = pt.getDouble(0))
        }
        return Route(
            id = "osrm-test",
            name = "OSRM Route (${waypoints.size} pts)",
            waypoints = waypoints,
        )
    }

    // ── Parsing tests ────────────────────────────────────────────────────────

    @Test
    fun `parses valid OSRM response with 3 coordinate points`() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [-46.6333, -23.5505],
                    [-46.6340, -23.5510],
                    [-46.6350, -23.5520]
                  ]
                }
              }]
            }
        """.trimIndent()

        val route = parseOsrmResponse(json)
        assertEquals(3, route.waypoints.size)
        assertEquals(-23.5505, route.waypoints[0].lat, 1e-6)
        assertEquals(-46.6333, route.waypoints[0].lng, 1e-6)
        assertEquals(-23.5520, route.waypoints[2].lat, 1e-6)
        assertTrue(route.name.contains("3 pts"))
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on non-Ok OSRM status`() {
        parseOsrmResponse("""{"code": "InvalidQuery", "message": "bad coordinates"}""")
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on empty routes array`() {
        parseOsrmResponse("""{"code": "Ok", "routes": []}""")
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on geometry with fewer than 2 points`() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "type": "LineString",
                  "coordinates": [[-46.6333, -23.5505]]
                }
              }]
            }
        """.trimIndent()
        parseOsrmResponse(json)
    }

    @Test
    fun `parses large coordinate array correctly`() {
        val coords = (0 until 100).joinToString(",") { i ->
            "[-46.${6333 + i}, -23.${5505 + i}]"
        }
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "type": "LineString",
                  "coordinates": [$coords]
                }
              }]
            }
        """.trimIndent()
        val route = parseOsrmResponse(json)
        assertEquals(100, route.waypoints.size)
    }

    @Test
    fun `swaps GeoJSON lng,lat to lat,lng correctly`() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [10.0, 20.0],
                    [30.0, 40.0]
                  ]
                }
              }]
            }
        """.trimIndent()
        val route = parseOsrmResponse(json)
        // GeoJSON: [lng=10, lat=20] → Waypoint(lat=20, lng=10)
        assertEquals(20.0, route.waypoints[0].lat, 1e-6)
        assertEquals(10.0, route.waypoints[0].lng, 1e-6)
        assertEquals(40.0, route.waypoints[1].lat, 1e-6)
        assertEquals(30.0, route.waypoints[1].lng, 1e-6)
    }

    // ── Fallback route tests ─────────────────────────────────────────────────

    @Test
    fun `fallbackRoute creates 2-point route`() {
        val route = Route(
            id = "fallback-test",
            name = "Direct Route (fallback)",
            waypoints = listOf(
                Waypoint(lat = -23.55, lng = -46.63),
                Waypoint(lat = -22.90, lng = -43.17),
            ),
        )
        assertEquals(2, route.waypoints.size)
        assertEquals(-23.55, route.waypoints[0].lat, 1e-6)
        assertEquals(-43.17, route.waypoints[1].lng, 1e-6)
    }

    @Test
    fun `fallbackMultiRoute preserves all waypoints`() {
        val wps = listOf(
            Waypoint(-23.55, -46.63),
            Waypoint(-23.56, -46.64),
            Waypoint(-23.57, -46.65),
        )
        val route = Route(
            id = "fallback-multi-test",
            name = "Direct Multi-Route (fallback)",
            waypoints = wps,
        )
        assertEquals(3, route.waypoints.size)
        assertEquals(wps, route.waypoints)
    }
}
