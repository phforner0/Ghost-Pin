package com.ghostpin.core.math

import kotlin.math.*

/**
 * Centralised geographic utility functions.
 *
 * Previously duplicated across RouteInterpolator, RouteRepository,
 * RouteEditorViewModel, and TrajectoryValidator. Consolidated here
 * so all modules share a single, tested implementation.
 */
object GeoMath {
    /** Mean Earth radius in metres (WGS-84 approximation). */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Haversine distance between two WGS-84 points, in metres.
     *
     * @param lat1 Latitude of point A in degrees.
     * @param lng1 Longitude of point A in degrees.
     * @param lat2 Latitude of point B in degrees.
     * @param lng2 Longitude of point B in degrees.
     * @return Great-circle distance in metres.
     */
    fun haversineMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
            sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return EARTH_RADIUS_M * 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Initial bearing from (lat1, lng1) to (lat2, lng2) in degrees [0, 360).
     *
     * @return Forward azimuth in degrees, normalised to [0, 360).
     */
    fun bearingBetween(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLng) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLng)
        val result = Math.toDegrees(atan2(y, x))
        return ((result + 360.0) % 360.0).toFloat()
    }
}
