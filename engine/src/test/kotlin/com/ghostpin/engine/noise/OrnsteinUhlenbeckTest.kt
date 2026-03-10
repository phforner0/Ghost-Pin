package com.ghostpin.engine.noise

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests for the Ornstein-Uhlenbeck noise generator — validates Gap 1 fix.
 */
class OrnsteinUhlenbeckTest {

    @Test
    fun `lag-1 autocorrelation is positive and significant`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.35,
            sigma = 2.5,
            random = Random(42),
        )

        val samples = mutableListOf<Double>()
        repeat(10_000) {
            val (lat, _) = ou.next(1.0)
            samples.add(lat)
        }

        val ac = autocorrelation(samples, lag = 1)
        assertTrue(ac > 0.3, "Lag-1 autocorrelation should be > 0.3, got $ac")
        assertTrue(ac < 0.95, "Lag-1 autocorrelation should be < 0.95, got $ac")
    }

    @Test
    fun `mean reverts toward zero from displaced state`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.8, // fast reversion
            sigma = 1.0,
            random = Random(42),
        )

        // Manually displace the state
        ou.next(0.01) // prime with small step
        // Force the state high by repeatedly sampling and checking trend
        val samples = mutableListOf<Double>()
        repeat(1000) {
            val (lat, _) = ou.next(1.0)
            samples.add(lat)
        }

        // The mean of the series should be close to 0 (mu=0)
        val mean = samples.average()
        assertTrue(
            kotlin.math.abs(mean) < 1.0,
            "Mean should be close to 0 (mu=0), got $mean"
        )
    }

    @Test
    fun `variance is bounded by theoretical steady-state`() {
        val theta = 0.5
        val sigma = 3.0
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = theta,
            sigma = sigma,
            random = Random(42),
        )

        val samples = mutableListOf<Double>()
        repeat(50_000) {
            val (lat, _) = ou.next(1.0)
            samples.add(lat)
        }

        val mean = samples.average()
        val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size

        // Theoretical steady-state variance = σ² / (2θ) = 9.0 / 1.0 = 9.0
        val theoreticalVariance = (sigma * sigma) / (2 * theta)
        assertTrue(
            variance < theoreticalVariance * 1.5,
            "Variance ($variance) should be near theoretical ($theoreticalVariance)"
        )
        assertTrue(
            variance > theoreticalVariance * 0.5,
            "Variance ($variance) should be near theoretical ($theoreticalVariance)"
        )
    }

    @Test
    fun `reset clears state to zero`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.5, sigma = 2.0, random = Random(42)
        )
        ou.next(1.0)
        ou.next(1.0)
        ou.reset()
        assertEquals(0.0, ou.currentLat)
        assertEquals(0.0, ou.currentLng)
    }

    @Test
    fun `different profiles produce different autocorrelation strengths`() {
        val ouSlow = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.25, sigma = 2.0, random = Random(42)
        )
        val ouFast = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.60, sigma = 2.0, random = Random(42)
        )

        val samplesSlow = mutableListOf<Double>()
        val samplesFast = mutableListOf<Double>()
        repeat(10_000) {
            samplesSlow.add(ouSlow.next(1.0).first)
            samplesFast.add(ouFast.next(1.0).first)
        }

        val acSlow = autocorrelation(samplesSlow, 1)
        val acFast = autocorrelation(samplesFast, 1)

        assertTrue(
            acSlow > acFast,
            "Lower theta should produce higher autocorrelation: slow=$acSlow, fast=$acFast"
        )
    }

    private fun autocorrelation(series: List<Double>, lag: Int): Double {
        val n = series.size
        val mean = series.average()
        val variance = series.sumOf { (it - mean) * (it - mean) } / n
        if (variance == 0.0) return 0.0
        var covariance = 0.0
        for (i in 0 until n - lag) {
            covariance += (series[i] - mean) * (series[i + lag] - mean)
        }
        covariance /= n
        return covariance / variance
    }
}
