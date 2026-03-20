package com.ghostpin.engine.interpolation

import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.math.GeoMath
import kotlin.math.*

/**
 * Interpolates positions along a [Route] using:
 *   - Catmull-Rom splines  when the route has ≥ 4 waypoints (smooth curves)
 *   - Linear interpolation when the route has 2–3 waypoints (straight segments)
 *
 * Parameterised by arc-length so that speed is uniform along the curve —
 * avoiding the "slow on curves, fast on straights" artefact of naive t-based splines.
 *
 * Usage:
 *   val interp = RouteInterpolator(route)
 *   val frame  = interp.positionAt(distanceTravelledMeters)
 */
class RouteInterpolator(val route: Route) {

    /** Cumulative distances in metres: cumulativeDistances[i] = metres from waypoint[0] to waypoint[i]. */
    val cumulativeDistances: DoubleArray

    /** Total length of the route in metres. */
    val totalDistanceMeters: Double

    init {
        val wps = route.waypoints
        require(wps.size >= 2) { "Route requires at least 2 waypoints, got ${wps.size}" }

        cumulativeDistances = DoubleArray(wps.size)
        cumulativeDistances[0] = 0.0
        for (i in 1 until wps.size) {
            cumulativeDistances[i] = cumulativeDistances[i - 1] +
                haversineMeters(wps[i - 1].lat, wps[i - 1].lng, wps[i].lat, wps[i].lng)
        }
        totalDistanceMeters = cumulativeDistances.last()
    }

    /**
     * Returns the interpolated position at [distanceMeters] along the route.
     *
     * @param distanceMeters Distance travelled from the start (0 .. totalDistanceMeters).
     *                       Values outside this range are clamped.
     * @return [InterpolatedFrame] with lat, lng, bearing and normalised progress [0,1].
     */
    fun positionAt(distanceMeters: Double): InterpolatedFrame {
        val clamped  = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        val progress = if (totalDistanceMeters > 0.0) clamped / totalDistanceMeters else 1.0
        val wps      = route.waypoints

        // Edge cases
        if (wps.size == 1) return InterpolatedFrame(wps[0].lat, wps[0].lng, 0f, 1.0)
        if (clamped >= totalDistanceMeters) {
            val last   = wps.last()
            val prev   = wps[wps.size - 2]
            val bearing = bearingBetween(prev.lat, prev.lng, last.lat, last.lng)
            return InterpolatedFrame(last.lat, last.lng, bearing, 1.0)
        }

        // Find segment index via binary search on cumulative distances
        val segIndex = upperBound(cumulativeDistances, clamped)
            .coerceIn(1, wps.size - 1) - 1

        val segStart  = cumulativeDistances[segIndex]
        val segEnd    = cumulativeDistances[segIndex + 1]
        val segLength = segEnd - segStart
        val t         = if (segLength > 0.0) (clamped - segStart) / segLength else 0.0

        return if (wps.size >= 4) {
            catmullRomFrame(wps, segIndex, t, progress)
        } else {
            linearFrame(wps, segIndex, t, progress)
        }
    }

    /**
     * Returns the distance in metres from the start to the waypoint at [index].
     */
    fun distanceToWaypoint(index: Int): Double =
        cumulativeDistances[index.coerceIn(0, cumulativeDistances.size - 1)]

    // ── Catmull-Rom ───────────────────────────────────────────────────────────

    private fun catmullRomFrame(
        wps: List<Waypoint>,
        segIndex: Int,
        t: Double,
        progress: Double,
    ): InterpolatedFrame {
        // Phantom ghost points for smooth tangents at start / end
        val p0 = if (segIndex > 0) wps[segIndex - 1] else {
            val a = wps[0]; val b = wps[1]
            Waypoint(lat = 2.0 * a.lat - b.lat, lng = 2.0 * a.lng - b.lng)
        }
        val p1 = wps[segIndex]
        val p2 = wps[segIndex + 1]
        val p3 = if (segIndex + 2 < wps.size) wps[segIndex + 2] else {
            val a = wps[wps.size - 2]; val b = wps[wps.size - 1]
            Waypoint(lat = 2.0 * b.lat - a.lat, lng = 2.0 * b.lng - a.lng)
        }

        val lat     = catmullRom(p0.lat, p1.lat, p2.lat, p3.lat, t)
        val lng     = catmullRom(p0.lng, p1.lng, p2.lng, p3.lng, t)
        // Derive bearing from the spline tangent at t (more accurate than p1→p2)
        val dt      = 0.01.coerceAtMost(1.0 - t)
        val latAhead = catmullRom(p0.lat, p1.lat, p2.lat, p3.lat, t + dt)
        val lngAhead = catmullRom(p0.lng, p1.lng, p2.lng, p3.lng, t + dt)
        val bearing = bearingBetween(lat, lng, latAhead, lngAhead)
            .takeIf { it.isFinite() }
            ?: bearingBetween(p1.lat, p1.lng, p2.lat, p2.lng)

        return InterpolatedFrame(lat, lng, bearing, progress)
    }

    /**
     * Standard Catmull-Rom formula for a single coordinate component.
     * α = 0.5 (centripetal parameterisation recommended but uniform is fine here).
     */
    private fun catmullRom(
        p0: Double, p1: Double, p2: Double, p3: Double, t: Double,
    ): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }

    // ── Linear ───────────────────────────────────────────────────────────────

    private fun linearFrame(
        wps: List<Waypoint>,
        segIndex: Int,
        t: Double,
        progress: Double,
    ): InterpolatedFrame {
        val p1 = wps[segIndex]
        val p2 = wps[segIndex + 1]
        val lat = p1.lat + (p2.lat - p1.lat) * t
        val lng = p1.lng + (p2.lng - p1.lng) * t
        val bearing = bearingBetween(p1.lat, p1.lng, p2.lat, p2.lng)
        return InterpolatedFrame(lat, lng, bearing, progress)
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────

    /** Finds the first index i where arr[i] > value (similar to std::upper_bound). */
    private fun upperBound(arr: DoubleArray, value: Double): Int {
        var lo = 0; var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid] <= value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {

        /**
         * Haversine distance between two WGS-84 points, in metres.
         * Delegates to [GeoMath.haversineMeters].
         */
        fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
            GeoMath.haversineMeters(lat1, lng1, lat2, lng2)

        /**
         * Initial bearing from (lat1,lng1) to (lat2,lng2) in degrees [0, 360).
         * Delegates to [GeoMath.bearingBetween].
         */
        fun bearingBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float =
            GeoMath.bearingBetween(lat1, lng1, lat2, lng2)
    }
}

/**
 * Result of a single [RouteInterpolator.positionAt] call.
 *
 * @param lat       Latitude at this point on the route.
 * @param lng       Longitude at this point on the route.
 * @param bearing   Direction of travel in degrees [0, 360).
 * @param progress  Fraction of total route completed [0.0, 1.0].
 */
data class InterpolatedFrame(
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val progress: Double,
)

enum class RepeatPolicy {
    NONE,
    LOOP_N,
    LOOP_INFINITE,
    PING_PONG_N,
    PING_PONG_INFINITE,
}

data class RepeatTraversalState(
    val progress: Double = 0.0,
    val direction: Int = 1, // +1 forward, -1 backward
    val currentLap: Int = 1,
    val completed: Boolean = false,
)

class RepeatTraversalController(
    private val policy: RepeatPolicy,
    private val repeatCount: Int,
) {
    init {
        if (policy == RepeatPolicy.LOOP_N || policy == RepeatPolicy.PING_PONG_N) {
            require(repeatCount >= 1) { "repeatCount must be >= 1 for $policy" }
        }
    }

    fun totalLapsLabel(): String = when (policy) {
        RepeatPolicy.NONE -> "1"
        RepeatPolicy.LOOP_N, RepeatPolicy.PING_PONG_N -> repeatCount.toString()
        RepeatPolicy.LOOP_INFINITE, RepeatPolicy.PING_PONG_INFINITE -> "∞"
    }

    fun advance(state: RepeatTraversalState, deltaProgress: Double): RepeatTraversalState {
        if (state.completed) return state

        var progress = state.progress + deltaProgress * state.direction
        var direction = state.direction
        var lap = state.currentLap
        var completed = false

        while (true) {
            if (direction > 0 && progress >= 1.0) {
                val overflow = progress - 1.0
                when (policy) {
                    RepeatPolicy.NONE -> {
                        progress = 1.0
                        completed = true
                    }
                    RepeatPolicy.LOOP_N -> {
                        if (lap >= repeatCount) {
                            progress = 1.0
                            completed = true
                        } else {
                            lap += 1
                            progress = overflow
                        }
                    }
                    RepeatPolicy.LOOP_INFINITE -> {
                        lap += 1
                        progress = overflow
                    }
                    RepeatPolicy.PING_PONG_N -> {
                        if (lap >= repeatCount) {
                            progress = 1.0
                            completed = true
                        } else {
                            lap += 1
                            direction = -1
                            progress = 1.0 - overflow
                        }
                    }
                    RepeatPolicy.PING_PONG_INFINITE -> {
                        lap += 1
                        direction = -1
                        progress = 1.0 - overflow
                    }
                }
            } else if (direction < 0 && progress <= 0.0) {
                val overflow = -progress
                when (policy) {
                    RepeatPolicy.NONE,
                    RepeatPolicy.LOOP_N,
                    RepeatPolicy.LOOP_INFINITE -> {
                        progress = 0.0
                    }
                    RepeatPolicy.PING_PONG_N -> {
                        if (lap >= repeatCount) {
                            progress = 0.0
                            completed = true
                        } else {
                            lap += 1
                            direction = 1
                            progress = overflow
                        }
                    }
                    RepeatPolicy.PING_PONG_INFINITE -> {
                        lap += 1
                        direction = 1
                        progress = overflow
                    }
                }
            } else {
                break
            }
            if (completed) break
        }

        return RepeatTraversalState(
            progress = progress.coerceIn(0.0, 1.0),
            direction = direction,
            currentLap = lap,
            completed = completed,
        )
    }
}
