package com.ghostpin.app.routing

import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports [Route] domain models to GPS track file formats.
 *
 * Sprint 4 — Task 16.
 *
 * Supported output formats:
 *  - **GPX 1.1**: Standard GPS Exchange Format. Most compatible with third-party tools.
 *  - **KML**: Google's Keyhole Markup Language, compatible with Google Maps/Earth.
 *  - **TCX**: Training Center XML, used by Garmin/fitness apps.
 *
 * All coordinate output uses 7 decimal places (~1 cm precision at the equator),
 * matching GhostPin's internal quantization precision.
 */
@Singleton
class RouteFileExporter @Inject constructor() {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ── GPX ──────────────────────────────────────────────────────────────────

    /**
     * Export route as GPX 1.1.
     *
     * @param route The route to export.
     * @return GPX XML string, ready to write to a .gpx file.
     */
    fun toGpx(route: Route): String {
        val now = isoDateFormat.format(Date())
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="GhostPin v3" """)
            appendLine("""  xmlns="http://www.topografix.com/GPX/1/1" """)
            appendLine("""  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """)
            appendLine("""  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")
            appendLine("""  <metadata>""")
            appendLine("""    <name>${route.name.xmlEscape()}</name>""")
            appendLine("""    <time>$now</time>""")
            appendLine("""  </metadata>""")
            appendLine("""  <trk>""")
            appendLine("""    <name>${route.name.xmlEscape()}</name>""")
            appendLine("""    <trkseg>""")
            route.waypoints.forEach { wp ->
                appendLine("""      <trkpt lat="${wp.lat.fmt()}" lon="${wp.lng.fmt()}">""")
                if (wp.altitude != 0.0) appendLine("""        <ele>${wp.altitude.fmt()}</ele>""")
                val label = wp.label
                if (label != null) appendLine("""        <n>${label.xmlEscape()}</n>""")
                appendLine("""      </trkpt>""")
            }
            appendLine("""    </trkseg>""")
            appendLine("""  </trk>""")
            appendLine("""</gpx>""")
        }
    }

    // ── KML ──────────────────────────────────────────────────────────────────

    /**
     * Export route as KML.
     *
     * @param route The route to export.
     * @return KML XML string, ready to write to a .kml file.
     */
    fun toKml(route: Route): String {
        val coords = route.waypoints.joinToString("\n          ") { wp ->
            "${wp.lng.fmt()},${wp.lat.fmt()},${wp.altitude.fmt()}"
        }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("""  <Document>""")
            appendLine("""    <name>${route.name.xmlEscape()}</name>""")
            appendLine("""    <Placemark>""")
            appendLine("""      <name>${route.name.xmlEscape()}</name>""")
            appendLine("""      <LineString>""")
            appendLine("""        <altitudeMode>clampToGround</altitudeMode>""")
            appendLine("""        <coordinates>""")
            appendLine("""          $coords""")
            appendLine("""        </coordinates>""")
            appendLine("""      </LineString>""")
            appendLine("""    </Placemark>""")
            appendLine("""  </Document>""")
            appendLine("""</kml>""")
        }
    }

    // ── TCX ──────────────────────────────────────────────────────────────────

    /**
     * Export route as TCX (Training Center XML).
     *
     * @param route The route to export.
     * @param sport Activity sport type for TCX metadata (default "Other").
     * @return TCX XML string, ready to write to a .tcx file.
     */
    fun toTcx(route: Route, sport: String = "Other"): String {
        val now = isoDateFormat.format(Date())
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""")
            appendLine("""  <Activities>""")
            appendLine("""    <Activity Sport="${sport.xmlEscape()}">""")
            appendLine("""      <Id>$now</Id>""")
            appendLine("""      <Lap StartTime="$now">""")
            appendLine("""        <Track>""")
            route.waypoints.forEach { wp ->
                appendLine("""          <Trackpoint>""")
                appendLine("""            <Position>""")
                appendLine("""              <LatitudeDegrees>${wp.lat.fmt()}</LatitudeDegrees>""")
                appendLine("""              <LongitudeDegrees>${wp.lng.fmt()}</LongitudeDegrees>""")
                appendLine("""            </Position>""")
                if (wp.altitude != 0.0) {
                    appendLine("""            <AltitudeMeters>${wp.altitude.fmt()}</AltitudeMeters>""")
                }
                appendLine("""          </Trackpoint>""")
            }
            appendLine("""        </Track>""")
            appendLine("""      </Lap>""")
            appendLine("""    </Activity>""")
            appendLine("""  </Activities>""")
            appendLine("""</TrainingCenterDatabase>""")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Format a coordinate to 7 decimal places. */
    private fun Double.fmt() = "%.7f".format(this)

    /** Escape XML special characters in route names / labels. */
    private fun String.xmlEscape() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
