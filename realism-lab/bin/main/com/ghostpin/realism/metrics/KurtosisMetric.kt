package com.ghostpin.realism.metrics

import kotlin.math.pow

/**
 * Computes excess kurtosis of a distribution.
 *
 * Validates that the multipath noise model (Gap 2 fix) produces a
 * heavy-tailed error distribution. A Gaussian has kurtosis = 3
 * (excess kurtosis = 0). With Laplace multipath jumps mixed in,
 * the combined distribution should have excess kurtosis > 0
 * (leptokurtic = heavier tails than Gaussian).
 *
 * Excess Kurtosis = μ₄/σ⁴ - 3
 * where μ₄ is the 4th central moment and σ⁴ is variance squared.
 */
object KurtosisMetric {

    /**
     * Compute excess kurtosis of a sample.
     *
     * @param series Data values.
     * @return Excess kurtosis. 0 for Gaussian, > 0 for heavy-tailed.
     */
    fun compute(series: List<Double>): Double {
        if (series.size < 4) return Double.NaN

        val n = series.size.toDouble()
        val mean = series.average()
        val m2 = series.sumOf { (it - mean).pow(2) } / n
        val m4 = series.sumOf { (it - mean).pow(4) } / n

        if (m2 == 0.0) return Double.NaN

        // Excess kurtosis (subtracting 3 for the Gaussian baseline)
        return (m4 / (m2 * m2)) - 3.0
    }

    /**
     * Validate that excess kurtosis exceeds the threshold for heavy tails.
     *
     * @param series Data values.
     * @param minExcessKurtosis Minimum acceptable excess kurtosis (default > 0).
     * @return [MetricResult] with pass/fail.
     */
    fun validate(series: List<Double>, minExcessKurtosis: Double = 0.0): MetricResult {
        val k = compute(series)
        return MetricResult(
            name = "Excess Kurtosis",
            value = k,
            passed = k > minExcessKurtosis,
            detail = "Expected > $minExcessKurtosis, got ${"%.4f".format(k)}",
        )
    }
}
