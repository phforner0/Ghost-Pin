package com.ghostpin.app.routing

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [RouteFileParser] — GPX, KML, and TCX parsing.
 *
 * Sprint 4 — Task 16.
 */
class RouteFileParserTest {

    private val parser = RouteFileParser()

    // ── GPX ──────────────────────────────────────────────────────────────────

    @Test
    fun `parses valid GPX track points`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test">
              <trk><n>Test Track</n><trkseg>
                <trkpt lat="-23.5505" lon="-46.6333"><ele>760.0</ele></trkpt>
                <trkpt lat="-23.5520" lon="-46.6350"><ele>761.0</ele></trkpt>
                <trkpt lat="-23.5540" lon="-46.6370"><ele>762.0</ele></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val result = parser.parse(gpx)
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(3, route.waypoints.size)
        assertEquals(-23.5505, route.waypoints[0].lat, 1e-6)
        assertEquals(-46.6333, route.waypoints[0].lng, 1e-6)
        assertEquals(760.0, route.waypoints[0].altitude, 0.01)
        assertEquals("Test Track", route.name)
    }

    @Test
    fun `parses GPX route points as fallback when no track points`() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <rte>
                <rtept lat="10.0" lon="20.0"/>
                <rtept lat="10.1" lon="20.1"/>
              </rte>
            </gpx>
        """.trimIndent()

        val result = parser.parse(gpx)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().waypoints.size)
    }

    @Test
    fun `GPX parse fails with fewer than 2 valid waypoints`() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <trk><trkseg>
                <trkpt lat="-23.55" lon="-46.63"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val result = parser.parse(gpx)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("waypoints") == true)
    }

    @Test
    fun `GPX skips invalid coordinates`() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <trk><trkseg>
                <trkpt lat="999.0" lon="-46.0"/>
                <trkpt lat="-23.55" lon="-46.63"/>
                <trkpt lat="-23.56" lon="-46.64"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val result = parser.parse(gpx)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().waypoints.size) // invalid coord skipped
    }

    // ── KML ──────────────────────────────────────────────────────────────────

    @Test
    fun `parses valid KML coordinates`() {
        val kml = """
            <?xml version="1.0"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <n>KML Route</n>
                <Placemark>
                  <LineString>
                    <coordinates>
                      -46.6333,-23.5505,760
                      -46.6350,-23.5520,761
                      -46.6370,-23.5540,762
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parser.parse(kml)
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(3, route.waypoints.size)
        // KML is lng,lat — verify they are correctly assigned
        assertEquals(-23.5505, route.waypoints[0].lat, 1e-6)
        assertEquals(-46.6333, route.waypoints[0].lng, 1e-6)
        assertEquals(760.0, route.waypoints[0].altitude, 0.01)
    }

    @Test
    fun `KML parse fails on zero-length route`() {
        val kml = """
            <?xml version="1.0"?>
            <kml><Document><Placemark><LineString>
              <coordinates>
                -46.6333,-23.5505,0
                -46.6333,-23.5505,0
              </coordinates>
            </LineString></Placemark></Document></kml>
        """.trimIndent()

        val result = parser.parse(kml)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("zero length") == true)
    }

    // ── TCX ──────────────────────────────────────────────────────────────────

    @Test
    fun `parses valid TCX trackpoints`() {
        val tcx = """
            <?xml version="1.0"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-03-10T12:00:00Z</Id>
                  <Lap StartTime="2026-03-10T12:00:00Z">
                    <Track>
                      <Trackpoint>
                        <Position>
                          <LatitudeDegrees>-23.5505</LatitudeDegrees>
                          <LongitudeDegrees>-46.6333</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>760.0</AltitudeMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Position>
                          <LatitudeDegrees>-23.5520</LatitudeDegrees>
                          <LongitudeDegrees>-46.6350</LongitudeDegrees>
                        </Position>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = parser.parse(tcx)
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(2, route.waypoints.size)
        assertEquals(-23.5505, route.waypoints[0].lat, 1e-6)
        assertEquals(760.0, route.waypoints[0].altitude, 0.01)
    }

    // ── Format detection ──────────────────────────────────────────────────────

    @Test
    fun `detects GPX format correctly`() {
        val content = "<gpx version=\"1.1\">...</gpx>"
        assertEquals(RouteFileParser.RouteFormat.GPX, RouteFileParser.detectFormat(content))
    }

    @Test
    fun `detects KML format correctly`() {
        val content = "<kml xmlns=\"http://www.opengis.net/kml/2.2\">...</kml>"
        assertEquals(RouteFileParser.RouteFormat.KML, RouteFileParser.detectFormat(content))
    }

    @Test
    fun `detects TCX format correctly`() {
        val content = "<TrainingCenterDatabase>...</TrainingCenterDatabase>"
        assertEquals(RouteFileParser.RouteFormat.TCX, RouteFileParser.detectFormat(content))
    }

    @Test
    fun `returns null for unknown format`() {
        val content = "<json>this is not XML</json>"
        assertNull(RouteFileParser.detectFormat(content))
    }

    // ── Name override ─────────────────────────────────────────────────────────

    @Test
    fun `name override takes precedence over file track name`() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <trk><n>Original Name</n><trkseg>
                <trkpt lat="-23.55" lon="-46.63"/>
                <trkpt lat="-23.56" lon="-46.64"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val result = parser.parse(gpx, name = "Custom Name")
        assertTrue(result.isSuccess)
        assertEquals("Custom Name", result.getOrThrow().name)
    }
}
