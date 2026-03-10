package com.ghostpin.realism.metrics

/**
 * Computes lag-k autocorrelation of a time series.
 *
 * Validates that the OU noise model (Gap 1 fix) is producing
 * temporally-correlated noise. For a properly calibrated OU process,
 * the lag-1 autocorrelation should be > 0.3 (typically 0.5–0.8).
 *
 * Independent (Box-Muller) noise would have lag-1 ≈ 0 — which is
 * exactly the detection vector the audit identified.
 */
object AutocorrelationMetric {

    /**
     * Compute the autocorrelation at the specified lag.
     *
     * @param series Time series of noise values.
     * @param lag Lag offset (default 1).
     * @return Autocorrelation coefficient in [-1, 1]. NaN if insufficient data.
     */
    fun compute(series: List<Double>, lag: Int = 1): Double {
        if (series.size <= lag) return Double.NaN

        val n = series.size
        val mean = series.average()
        val variance = series.sumOf { (it - mean) * (it - mean) } / n

        if (variance == 0.0) return Double.NaN

        var covariance = 0.0
        for (i in 0 until n - lag) {
            covariance += (series[i] - mean) * (series[i + lag] - mean)
        }
        covariance /= n

        return covariance / variance
    }

    /**
     * Validate that autocorrelation is within expected range for OU process.
     *
     * @param series Noise time series.
     * @param minLag1 Minimum acceptable lag-1 autocorrelation.
     * @param maxLag1 Maximum acceptable lag-1 autocorrelation.
     * @return [MetricResult] with pass/fail and computed value.
     */
    fun validate(
        series: List<Double>,
        minLag1: Double = 0.3,
        maxLag1: Double = 0.95,
    ): MetricResult {
        val ac = compute(series, lag = 1)
        return MetricResult(
            name = "Autocorrelation (lag-1)",
            value = ac,
            passed = ac in minLag1..maxLag1,
            detail = "Expected ∈ [$minLag1, $maxLag1], got ${"%.4f".format(ac)}",
        )
    }
}
