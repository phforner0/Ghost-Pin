package com.ghostpin.realism.metrics

import com.ghostpin.core.model.NoiseVector
import kotlin.math.abs

/**
 * Validates multipath jump frequency and magnitude against expected parameters.
 *
 * Ensures that:
 * - The observed jump frequency matches the configured pMultipath ± tolerance
 * - Jump magnitudes are within plausible Laplace distribution range
 */
object JumpFrequencyMetric {

    /**
     * Compute the observed jump frequency from a series of noise vectors.
     *
     * @param samples List of noise vectors from simulation.
     * @return Fraction of samples flagged as jumps (0..1).
     */
    fun computeFrequency(samples: List<NoiseVector>): Double {
        if (samples.isEmpty()) return 0.0
        return samples.count { it.isJump }.toDouble() / samples.size
    }

    /**
     * Compute mean magnitude of jump events.
     *
     * @param samples List of noise vectors from simulation.
     * @return Mean of √(lat² + lng²) for jump events, or NaN if no jumps.
     */
    fun computeMeanMagnitude(samples: List<NoiseVector>): Double {
        val jumps = samples.filter { it.isJump }
        if (jumps.isEmpty()) return Double.NaN
        return jumps.map { Math.sqrt(it.lat * it.lat + it.lng * it.lng) }.average()
    }

    /**
     * Validate jump frequency against expected pMultipath.
     *
     * @param samples Noise vectors from simulation.
     * @param expectedP Expected multipath probability.
     * @param tolerance Acceptable deviation from expected (default 0.02).
     * @return [MetricResult] with pass/fail.
     */
    fun validate(
        samples: List<NoiseVector>,
        expectedP: Double,
        tolerance: Double = 0.02,
    ): MetricResult {
        val observed = computeFrequency(samples)
        val passed = abs(observed - expectedP) <= tolerance
        return MetricResult(
            name = "Jump Frequency",
            value = observed,
            passed = passed,
            detail = "Expected ${"%.3f".format(expectedP)} ± $tolerance, " +
                "got ${"%.3f".format(observed)}",
        )
    }
}
