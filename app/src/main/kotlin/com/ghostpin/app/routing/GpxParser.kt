package com.ghostpin.app.routing

import android.util.Xml
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses `.gpx` files (GPS Exchange Format) into a [Route] that the
 * GhostPin engine can simulate.
 *
 * This implementation uses [XmlPullParser] to efficiently extract `<trkpt>`
 * or `<wpt>` coordinates without loading the entire XML document into memory
 * (DOM), avoiding OOM on large, dense recorded tracks.
 *
 * Fix: Route constructor now supplies the required `id` and `name` fields and
 * drops the non-existent `distanceMeters` / `durationSeconds` parameters that
 * were causing a compilation error and cascading KSP/Hilt failures.
 */
@Singleton
class GpxParser @Inject constructor(
    private val routeFileParser: RouteFileParser,
) {

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
    fun parse(inputStream: InputStream, filename: String? = null): Result<Route> {
        inputStream.markSupported()
        return try {
            val bytes = inputStream.readBytes()
            val delegated = routeFileParser.parse(bytes.inputStream(), filename ?: "Imported Route")
            if (delegated.isSuccess) return delegated

            val parser: XmlPullParser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(bytes.inputStream(), null)
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
                            if (lat in -90.0..90.0 && lng in -180.0..180.0 && lat.isFinite() && lng.isFinite()) {
                                waypoints.add(Waypoint(lat, lng))
                            }
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
                // Fix (🔴): Route requires `id` and `name` — previously called with
                // non-existent `distanceMeters` and `durationSeconds` parameters,
                // which caused a Kotlin compilation error and cascading KSP failures.
                Result.success(Route(
                    id = "gpx_${System.currentTimeMillis()}",
                    name = "Imported GPX Route",
                    waypoints = waypoints
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

    /** Forward-skips the parser to the end of an uninteresting element to avoid deep parsing overhead. */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG   -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
