package com.ghostpin.realism.metrics

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.NoiseVector
import com.ghostpin.engine.noise.MultipathNoiseModel
import com.ghostpin.engine.noise.OrnsteinUhlenbeckNoiseGenerator
import com.ghostpin.realism.RealismReport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests for the Realism Lab metrics suite.
 */
class RealismMetricsTest {

    @Test
    fun `autocorrelation detects correlated OU noise`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.35, sigma = 2.5, random = Random(42)
        )
        val series = (0 until 5000).map { ou.next(1.0).first }
        val result = AutocorrelationMetric.validate(series)
        assertTrue(result.passed, "OU noise should have significant autocorrelation: ${result.detail}")
    }

    @Test
    fun `autocorrelation detects uncorrelated noise as failure`() {
        val rng = Random(42)
        val iidSeries = (0 until 5000).map { rng.nextGaussian() * 2.5 }
        val result = AutocorrelationMetric.validate(iidSeries, minLag1 = 0.3)
        assertFalse(result.passed, "i.i.d. noise should fail autocorrelation: ${result.detail}")
    }

    @Test
    fun `kurtosis detects heavy tails from multipath`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.5, sigma = 3.0, random = Random(42)
        )
        val model = MultipathNoiseModel(
            ouGenerator = ou,
            pMultipath = 0.05,
            laplaceScale = 15.0,
            random = Random(42),
        )
        val series = (0 until 50_000).map { model.sample(1.0).lat }
        val result = KurtosisMetric.validate(series, minExcessKurtosis = 0.0)
        assertTrue(result.passed, "Multipath noise should have kurtosis > 3: ${result.detail}")
    }

    @Test
    fun `kurtosis near zero for pure Gaussian`() {
        val rng = Random(42)
        val series = (0 until 50_000).map { rng.nextGaussian() * 3.0 }
        val k = KurtosisMetric.compute(series)
        assertTrue(
            kotlin.math.abs(k) < 0.5,
            "Pure Gaussian should have near-zero excess kurtosis, got $k"
        )
    }

    @Test
    fun `jump frequency metric validates correctly`() {
        val samples = (0 until 1000).map { i ->
            NoiseVector(lat = 0.0, lng = 0.0, isJump = i % 25 == 0) // 4%
        }
        val result = JumpFrequencyMetric.validate(samples, expectedP = 0.04, tolerance = 0.01)
        assertTrue(result.passed, "4% jump rate should match: ${result.detail}")
    }

    @Test
    fun `timestamp monotonicity detects violations`() {
        val locations = listOf(
            MockLocation(lat = 0.0, lng = 0.0, elapsedRealtimeNanos = 100L),
            MockLocation(lat = 0.0, lng = 0.0, elapsedRealtimeNanos = 200L),
            MockLocation(lat = 0.0, lng = 0.0, elapsedRealtimeNanos = 150L), // violation!
            MockLocation(lat = 0.0, lng = 0.0, elapsedRealtimeNanos = 300L),
        )
        val result = TimestampMonotonicityMetric.validate(locations)
        assertFalse(result.passed, "Should detect out-of-order timestamp")
    }

    @Test
    fun `timestamp monotonicity passes for ordered sequence`() {
        val locations = (0L until 100L).map {
            MockLocation(lat = 0.0, lng = 0.0, elapsedRealtimeNanos = it * 1_000_000L)
        }
        val result = TimestampMonotonicityMetric.validate(locations)
        assertTrue(result.passed, "Ordered timestamps should pass")
    }

    @Test
    fun `full realism report runs all metrics`() {
        val ou = OrnsteinUhlenbeckNoiseGenerator(
            theta = 0.45, sigma = 3.0, random = Random(42)
        )
        val model = MultipathNoiseModel(
            ouGenerator = ou,
            pMultipath = 0.03,
            laplaceScale = 10.0,
            random = Random(42),
        )

        val noiseVectors = mutableListOf<NoiseVector>()
        val latSeries = mutableListOf<Double>()
        repeat(10_000) {
            val nv = model.sample(1.0)
            noiseVectors.add(nv)
            latSeries.add(nv.lat)
        }

        val locations = (0 until 10_000).map { i ->
            MockLocation(
                lat = 0.0, lng = 0.0,
                elapsedRealtimeNanos = i * 1_000_000_000L,
            )
        }

        val report = RealismReport.evaluate(
            locations = locations,
            noiseVectors = noiseVectors,
            latNoiseSeries = latSeries,
            expectedPMultipath = 0.03,
        )

        assertEquals(4, report.total, "Should run 4 metrics")
        println(report.summary())
    }
}
