package com.ghostpin.app.routing

import android.util.Xml
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses `.gpx` files (GPS Exchange Format) into a [Route] that the
 * GhostPin engine can simulate.
 *
 * This implementation uses [XmlPullParser] to efficiently extract `<trkpt>`
 * or `<wpt>` coordinates without loading the entire XML document into memory
 * (DOM), avoiding OOM on large, dense recorded tracks.
 */
@Singleton
class GpxParser @Inject constructor() {

    // A minimal namespace setting for pull parser (usually null is fine)
    private val ns: String? = null

    /**
     * Reads a GPX input stream and returns a parsed [Route].
     *
     * @param inputStream The raw `.gpx` file stream.
     * @throws IOException If the connection/stream fails.
     * @throws XmlPullParserException If the XML format is malformed.
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): Result<Route> {
        return try {
            val parser: XmlPullParser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(inputStream, null)
                nextTag()
            }
            
            val waypoints = mutableListOf<Waypoint>()
            
            // Fast-forward to the root <gpx> tag
            parser.require(XmlPullParser.START_TAG, ns, "gpx")
            
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                
                val name = parser.name
                // Prioritize track points, but fallback to waypoints if it's a generic file
                if (name == "trkpt" || name == "wpt") {
                    val latAttr = parser.getAttributeValue(null, "lat")
                    val lonAttr = parser.getAttributeValue(null, "lon")
                    
                    if (latAttr != null && lonAttr != null) {
                        try {
                            val lat = latAttr.toDouble()
                            val lng = lonAttr.toDouble()
                            waypoints.add(Waypoint(lat, lng))
                        } catch (e: NumberFormatException) {
                            // Skip invalid float points gracefully
                        }
                    }
                    // Skip nested tags inside trkpt/wpt like <ele> or <time> to stay fast
                    skip(parser)
                } else {
                    // It's a structure tag like <trk>, <trkseg>, <metadata>, so dive in or skip
                    if (name != "trk" && name != "trkseg") {
                        skip(parser)
                    }
                }
            }
            
            if (waypoints.size >= 2) {
                Result.success(Route(
                    waypoints = waypoints,
                    distanceMeters = estimateDistance(waypoints),
                    durationSeconds = 0.0 // Handled cleanly by SpeedController later
                ))
            } else {
                Result.failure(IllegalArgumentException("GPX file contains fewer than 2 points."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inputStream.close()
        }
    }

    /** Fast distance estimation for raw file points using Haversine */
    private fun estimateDistance(points: List<Waypoint>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            distance += haversineMeters(p1.lat, p1.lng, p2.lat, p2.lng)
        }
        return distance
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth's radius in meters
        val a1 = Math.toRadians(lat1)
        val a2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(a1) * Math.cos(a2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /** Forward skips the parser to the end of an uninteresting element to avoid deep parsing overhead. */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
