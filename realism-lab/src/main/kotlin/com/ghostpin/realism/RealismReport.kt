package com.ghostpin.realism

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.NoiseVector
import com.ghostpin.realism.metrics.*

/**
 * Aggregated Realism Lab report — runs all metrics and produces a unified result.
 *
 * Designed to be run after a simulation session to validate that the noise
 * model is producing realistic GPS traces.
 */
data class RealismReport(
    val results: List<MetricResult>,
) {
    /** True if ALL metrics passed. */
    val allPassed: Boolean get() = results.all { it.passed }

    /** Count of passing metrics. */
    val passCount: Int get() = results.count { it.passed }

    /** Count of failing metrics. */
    val failCount: Int get() = results.count { !it.passed }

    /** Total number of metrics evaluated. */
    val total: Int get() = results.size

    /** Human-readable summary. */
    fun summary(): String = buildString {
        appendLine("=== Realism Report ===")
        appendLine("$passCount / $total metrics passed")
        appendLine()
        results.forEach { r ->
            val status = if (r.passed) "✅ PASS" else "❌ FAIL"
            appendLine("$status  ${r.name}: ${r.detail}")
        }
    }

    companion object {
        /**
         * Run all available metrics on the simulation output.
         *
         * @param locations Ordered sequence of mock locations produced.
         * @param noiseVectors Noise vectors produced (parallel to locations).
         * @param latNoiseSeries Lat noise values for autocorrelation/kurtosis.
         * @param expectedPMultipath Expected multipath probability from profile.
         * @return Aggregated [RealismReport].
         */
        fun evaluate(
            locations: List<MockLocation>,
            noiseVectors: List<NoiseVector>,
            latNoiseSeries: List<Double>,
            expectedPMultipath: Double,
        ): RealismReport {
            val results = mutableListOf<MetricResult>()

            // 1. Autocorrelation (OU validation)
            results += AutocorrelationMetric.validate(latNoiseSeries)

            // 2. Kurtosis (multipath validation)
            results += KurtosisMetric.validate(latNoiseSeries)

            // 3. Jump frequency
            results += JumpFrequencyMetric.validate(noiseVectors, expectedPMultipath)

            // 4. Timestamp monotonicity
            results += TimestampMonotonicityMetric.validate(locations)

            return RealismReport(results)
        }
    }
}
