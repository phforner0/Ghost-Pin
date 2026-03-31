package com.ghostpin.engine.noise

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.pow

/**
 * Tests for the multipath noise model — validates Gap 2 fix.
 */
class MultipathNoiseTest {
    @Test
    fun `excess kurtosis is positive with multipath`() {
        val ou =
            OrnsteinUhlenbeckNoiseGenerator(
                theta = 0.55,
                sigma = 4.0,
                random = Random(42),
            )
        val model =
            MultipathNoiseModel(
                ouGenerator = ou,
                pMultipath = 0.04,
                laplaceScale = 15.0,
                random = Random(42),
            )

        val samples = mutableListOf<Double>()
        repeat(50_000) {
            val noise = model.sample(1.0)
            samples.add(noise.lat)
        }

        val kurtosis = excessKurtosis(samples)
        assertTrue(
            kurtosis > 0.0,
            "Excess kurtosis should be > 0 (heavy tails), got $kurtosis"
        )
    }

    @Test
    fun `jump frequency matches pMultipath within tolerance`() {
        val pMultipath = 0.04
        val ou =
            OrnsteinUhlenbeckNoiseGenerator(
                theta = 0.35,
                sigma = 2.5,
                random = Random(123)
            )
        val model =
            MultipathNoiseModel(
                ouGenerator = ou,
                pMultipath = pMultipath,
                laplaceScale = 8.0,
                random = Random(123),
            )

        var jumpCount = 0
        val total = 100_000
        repeat(total) {
            val noise = model.sample(1.0)
            if (noise.isJump) jumpCount++
        }

        val observedP = jumpCount.toDouble() / total
        assertTrue(
            kotlin.math.abs(observedP - pMultipath) < 0.01,
            "Observed jump rate $observedP should be near $pMultipath"
        )
    }

    @Test
    fun `no jumps when pMultipath is zero`() {
        val ou =
            OrnsteinUhlenbeckNoiseGenerator(
                theta = 0.5,
                sigma = 2.0,
                random = Random(42)
            )
        val model =
            MultipathNoiseModel(
                ouGenerator = ou,
                pMultipath = 0.0,
                laplaceScale = 10.0,
                random = Random(42),
            )

        var jumpCount = 0
        repeat(10_000) {
            if (model.sample(1.0).isJump) jumpCount++
        }
        assertEquals(0, jumpCount, "No jumps expected when pMultipath=0")
    }

    @Test
    fun `reset clears jump state`() {
        val ou =
            OrnsteinUhlenbeckNoiseGenerator(
                theta = 0.5,
                sigma = 2.0,
                random = Random(42)
            )
        val model =
            MultipathNoiseModel(
                ouGenerator = ou,
                pMultipath = 1.0, // force jumps
                laplaceScale = 10.0,
                random = Random(42),
            )

        model.sample(1.0) // will be a jump
        assertTrue(model.lastWasJump)
        model.reset()
        assertFalse(model.lastWasJump)
    }

    private fun excessKurtosis(series: List<Double>): Double {
        val n = series.size.toDouble()
        val mean = series.average()
        val m2 = series.sumOf { (it - mean).pow(2) } / n
        val m4 = series.sumOf { (it - mean).pow(4) } / n
        return (m4 / (m2 * m2)) - 3.0
    }
}
