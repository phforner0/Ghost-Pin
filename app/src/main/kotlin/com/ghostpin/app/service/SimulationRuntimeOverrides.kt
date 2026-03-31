package com.ghostpin.app.service

import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.distanceMeters
import com.ghostpin.engine.interpolation.RepeatPolicy

data class SegmentRuntimeBehavior(
    val targetRatio: Double,
    val pauseSec: Double,
    val shouldRestartFromStart: Boolean,
)

internal fun resolveSegmentRuntimeBehavior(
    route: Route,
    segmentIndex: Int,
    profile: MovementProfile,
    baseSpeedRatio: Double,
    defaultWaypointPauseSec: Double,
    repeatPolicy: RepeatPolicy,
    triggeredLoopSegments: Set<Int>,
): SegmentRuntimeBehavior {
    val normalizedBaseRatio = baseSpeedRatio.coerceIn(0.0, 1.0)
    val segmentOverride = route.segments.getOrNull(segmentIndex)?.overrides
    val targetRatio = ((segmentOverride?.speedOverrideMs ?: (profile.maxSpeedMs * normalizedBaseRatio)) / profile.maxSpeedMs)
        .coerceIn(0.0, 1.0)
    val pauseSec = (segmentOverride?.pauseDurationSec ?: defaultWaypointPauseSec).coerceAtLeast(0.0)

    // Segment loop overrides are intentionally one-shot and only apply when
    // the global repeat policy is not already controlling traversal.
    val shouldRestartFromStart =
        segmentOverride?.loop == true &&
            repeatPolicy == RepeatPolicy.NONE &&
            segmentIndex !in triggeredLoopSegments

    return SegmentRuntimeBehavior(
        targetRatio = targetRatio,
        pauseSec = pauseSec,
        shouldRestartFromStart = shouldRestartFromStart,
    )
}

internal fun estimateRuntimeDurationSec(
    route: Route,
    profile: MovementProfile,
    baseSpeedRatio: Double = 1.0,
    defaultWaypointPauseSec: Double = 0.0,
): Double {
    val normalizedBaseRatio = baseSpeedRatio.coerceIn(0.0, 1.0)
    val baseSpeedMs = profile.maxSpeedMs * normalizedBaseRatio
    if (baseSpeedMs <= 0.0) return Double.MAX_VALUE

    if (route.segments.isNotEmpty()) {
        return route.segments.sumOf { segment ->
            val speedMs = segment.overrides?.speedOverrideMs ?: baseSpeedMs
            val movementTimeSec = if (speedMs > 0.0) segment.distance / speedMs else Double.MAX_VALUE
            val pauseSec = segment.overrides?.pauseDurationSec ?: defaultWaypointPauseSec
            movementTimeSec + pauseSec.coerceAtLeast(0.0)
        }
    }

    return (route.distanceMeters / baseSpeedMs) + defaultWaypointPauseSec.coerceAtLeast(0.0)
}
