package com.ghostpin.realism.metrics

import com.ghostpin.core.model.MockLocation

/**
 * Validates strict monotonicity of elapsedRealtimeNanos across a location sequence.
 *
 * Real GPS never produces out-of-order timestamps. Any non-monotonic timestamp
 * in the mock sequence would be trivially detectable.
 */
object TimestampMonotonicityMetric {

    /**
     * Check that all elapsedRealtimeNanos values are strictly increasing.
     *
     * @param locations Ordered sequence of mock locations.
     * @return [MetricResult] — fails if ANY pair is non-monotonic.
     */
    fun validate(locations: List<MockLocation>): MetricResult {
        if (locations.size < 2) {
            return MetricResult(
                name = "Timestamp Monotonicity",
                value = 1.0,
                passed = true,
                detail = "Insufficient data (< 2 locations)",
            )
        }

        var violations = 0
        var firstViolationIndex = -1
        for (i in 1 until locations.size) {
            if (locations[i].elapsedRealtimeNanos <= locations[i - 1].elapsedRealtimeNanos) {
                violations++
                if (firstViolationIndex == -1) firstViolationIndex = i
            }
        }

        return MetricResult(
            name = "Timestamp Monotonicity",
            value = if (violations == 0) 1.0 else 0.0,
            passed = violations == 0,
            detail = if (violations == 0) "All timestamps strictly increasing"
            else "$violations violation(s), first at index $firstViolationIndex",
        )
    }
}
