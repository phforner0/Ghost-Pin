package com.ghostpin.app.routing

import android.util.Log
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses GPS track files (GPX, KML, TCX) into [Route] domain models.
 *
 * Sprint 4 — Task 16.
 *
 * Supported formats:
 *  - **GPX 1.1**: reads `<trkpt>` (tracks) and `<rtept>` (routes), extracts
 *    `lat`, `lon`, optional `<ele>` (elevation), optional `<name>`.
 *  - **KML**: reads `<Placemark>/<LineString>/coordinates` as ordered waypoints.
 *    Each comma-separated triple is `lng,lat[,alt]`.
 *  - **TCX** (Training Center XML): reads `<Trackpoint>/<Position>` pairs with
 *    `<LatitudeDegrees>`, `<LongitudeDegrees>`, optional `<AltitudeMeters>`.
 *
 * Validation rules (applied to all formats):
 *  - At least 2 waypoints after parsing.
 *  - Latitude in [-90, 90], longitude in [-180, 180].
 *  - No NaN or Infinity coordinates.
 *  - If all waypoints are identical (0-length route), parsing fails.
 */
@Singleton
class RouteFileParser @Inject constructor() {

    companion object {
        private const val TAG = "RouteFileParser"
        private const val MIN_WAYPOINTS = 2

        /** Detect file format from raw content string. */
        fun detectFormat(content: String): RouteFormat? = when {
            content.contains("<gpx", ignoreCase = true)     -> RouteFormat.GPX
            content.contains("<kml", ignoreCase = true)     -> RouteFormat.KML
            content.contains("<TrainingCenterDatabase",
                ignoreCase = true)                          -> RouteFormat.TCX
            else -> null
        }
    }

    enum class RouteFormat { GPX, KML, TCX }

    /**
     * Parse a route file from its string content.
     *
     * @param content Raw file contents as UTF-8 string.
     * @param name Override route name (uses filename or track name from file if null).
     * @return [Result.success] with [Route] or [Result.failure] with a descriptive error.
     */
    fun parse(content: String, name: String? = null): Result<Route> {
        val format = detectFormat(content)
            ?: return Result.failure(IllegalArgumentException(
                "Unrecognized format. Expected GPX, KML, or TCX."
            ))

        return when (format) {
            RouteFormat.GPX -> parseGpx(content, name)
            RouteFormat.KML -> parseKml(content, name)
            RouteFormat.TCX -> parseTcx(content, name)
        }
    }

    // ── GPX ──────────────────────────────────────────────────────────────────

    private fun parseGpx(content: String, nameOverride: String?): Result<Route> = runCatching {
        val doc = parseXml(content)
        val waypoints = mutableListOf<Waypoint>()

        // Try track points first (<trk>/<trkseg>/<trkpt>)
        val trkptNodes = doc.getElementsByTagName("trkpt")
        for (i in 0 until trkptNodes.length) {
            val node = trkptNodes.item(i)
            val lat = node.attributes.getNamedItem("lat")?.nodeValue?.toDoubleOrNull() ?: continue
            val lon = node.attributes.getNamedItem("lon")?.nodeValue?.toDoubleOrNull() ?: continue
            val ele = node.childNodes.let { children ->
                (0 until children.length).firstNotNullOfOrNull { j ->
                    val child = children.item(j)
                    if (child.nodeName == "ele") child.textContent.toDoubleOrNull() else null
                }
            } ?: 0.0
            if (isValidCoord(lat, lon)) waypoints += Waypoint(lat, lon, ele)
        }

        // Fallback: route points (<rte>/<rtept>)
        if (waypoints.isEmpty()) {
            val rteptNodes = doc.getElementsByTagName("rtept")
            for (i in 0 until rteptNodes.length) {
                val node = rteptNodes.item(i)
                val lat = node.attributes.getNamedItem("lat")?.nodeValue?.toDoubleOrNull() ?: continue
                val lon = node.attributes.getNamedItem("lon")?.nodeValue?.toDoubleOrNull() ?: continue
                if (isValidCoord(lat, lon)) waypoints += Waypoint(lat, lon)
            }
        }

        // Extract track name
        val trackName = nameOverride ?: doc.getElementsByTagName("name").item(0)
            ?.textContent?.trim() ?: "Imported GPX Route"

        buildRoute(waypoints, trackName, "GPX")
    }.onFailure { e -> Log.e(TAG, "GPX parse error: ${e.message}") }

    // ── KML ──────────────────────────────────────────────────────────────────

    private fun parseKml(content: String, nameOverride: String?): Result<Route> = runCatching {
        val doc = parseXml(content)
        val waypoints = mutableListOf<Waypoint>()

        val coordNodes = doc.getElementsByTagName("coordinates")
        for (i in 0 until coordNodes.length) {
            val raw = coordNodes.item(i).textContent.trim()
            raw.split(Regex("\\s+")).forEach { triple ->
                val parts = triple.split(",")
                val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: return@forEach
                val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
                val alt = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                if (isValidCoord(lat, lng)) waypoints += Waypoint(lat, lng, alt)
            }
        }

        val placemarkName = nameOverride ?: doc.getElementsByTagName("name").item(0)
            ?.textContent?.trim() ?: "Imported KML Route"

        buildRoute(waypoints, placemarkName, "KML")
    }.onFailure { e -> Log.e(TAG, "KML parse error: ${e.message}") }

    // ── TCX ──────────────────────────────────────────────────────────────────

    private fun parseTcx(content: String, nameOverride: String?): Result<Route> = runCatching {
        val doc = parseXml(content)
        val waypoints = mutableListOf<Waypoint>()

        val trackpointNodes = doc.getElementsByTagName("Trackpoint")
        for (i in 0 until trackpointNodes.length) {
            val node = trackpointNodes.item(i)
            val children = node.childNodes
            var lat: Double? = null
            var lng: Double? = null
            var alt = 0.0

            for (j in 0 until children.length) {
                val child = children.item(j)
                when (child.nodeName) {
                    "Position" -> {
                        val pos = child.childNodes
                        for (k in 0 until pos.length) {
                            when (pos.item(k).nodeName) {
                                "LatitudeDegrees"  -> lat = pos.item(k).textContent.toDoubleOrNull()
                                "LongitudeDegrees" -> lng = pos.item(k).textContent.toDoubleOrNull()
                            }
                        }
                    }
                    "AltitudeMeters" -> alt = child.textContent.toDoubleOrNull() ?: 0.0
                }
            }

            if (lat != null && lng != null && isValidCoord(lat, lng)) {
                waypoints += Waypoint(lat, lng, alt)
            }
        }

        val activityName = nameOverride ?: doc.getElementsByTagName("Activity").item(0)
            ?.attributes?.getNamedItem("Sport")
            ?.nodeValue?.trim() ?: "Imported TCX Route"

        buildRoute(waypoints, activityName, "TCX")
    }.onFailure { e -> Log.e(TAG, "TCX parse error: ${e.message}") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseXml(content: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Harden against XXE (XML External Entity) attacks — SEC-01
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
    }

    private fun isValidCoord(lat: Double, lng: Double): Boolean =
        !lat.isNaN() && !lat.isInfinite() && lat in -90.0..90.0 &&
        !lng.isNaN() && !lng.isInfinite() && lng in -180.0..180.0

    private fun buildRoute(
        waypoints: List<Waypoint>,
        name: String,
        format: String,
    ): Route {
        require(waypoints.size >= MIN_WAYPOINTS) {
            "Parsed $format file contains ${waypoints.size} valid waypoints — need at least $MIN_WAYPOINTS."
        }

        // Detect zero-length routes (all identical coordinates)
        val allSame = waypoints.all {
            it.lat == waypoints.first().lat && it.lng == waypoints.first().lng
        }
        require(!allSame) {
            "All $format waypoints are identical — route has zero length."
        }

        Log.d(TAG, "Parsed $format: ${waypoints.size} waypoints → '$name'")
        return Route(
            id = UUID.randomUUID().toString(),
            name = name,
            waypoints = waypoints,
        )
    }
}
