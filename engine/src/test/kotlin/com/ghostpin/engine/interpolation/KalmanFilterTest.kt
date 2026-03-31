package com.ghostpin.engine.interpolation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class KalmanFilterTest {
    private val dt = 0.2 // 5 Hz — same as simulation default

    // ── Initialisation ────────────────────────────────────────────────────

    @Test
    fun `first update returns the measurement unchanged`() {
        val kf = KalmanFilter1D()
        val estimate = kf.update(42.0, dt)
        assertEquals(42.0, estimate, 1e-9)
    }

    // ── Convergence ───────────────────────────────────────────────────────

    @Test
    fun `converges to constant signal within reasonable iterations`() {
        val kf = KalmanFilter1D()
        val signal = -23.5505
        repeat(50) { kf.update(signal, dt) }
        val estimate = kf.update(signal, dt)
        assertEquals(
            signal,
            estimate,
            1e-4,
            "Expected convergence to $signal but got $estimate"
        )
    }

    @Test
    fun `converges when signal changes`() {
        val kf = KalmanFilter1D()
        // Settle on 0.0
        repeat(50) { kf.update(0.0, dt) }
        // Step change to 10.0 — needs more iterations with high measurementNoise (0.3)
        // because the filter strongly smooths, trading responsiveness for stability.
        repeat(500) { kf.update(10.0, dt) }
        val estimate = kf.update(10.0, dt)
        assertEquals(
            10.0,
            estimate,
            0.5,
            "Expected convergence to 10.0 but got $estimate"
        )
    }

    // ── Smoothing ─────────────────────────────────────────────────────────

    @Test
    fun `output variance is lower than input variance for noisy signal`() {
        val kf = KalmanFilter1D(processNoise = 1e-5, measurementNoise = 0.3)
        val random = java.util.Random(42L)
        val signal = -23.5505
        val noise = 0.0002 // ±20cm in degrees (~22m) — realistic GPS noise

        val inputs = mutableListOf<Double>()
        val outputs = mutableListOf<Double>()

        // Warm up
        repeat(20) { kf.update(signal + random.nextGaussian() * noise, dt) }

        // Collect samples
        repeat(200) {
            val measurement = signal + random.nextGaussian() * noise
            inputs.add(measurement)
            outputs.add(kf.update(measurement, dt))
        }

        val inputVar = variance(inputs)
        val outputVar = variance(outputs)

        assertTrue(
            outputVar < inputVar,
            "Expected Kalman to reduce variance: inputVar=$inputVar outputVar=$outputVar"
        )
    }

    // ── No NaN ────────────────────────────────────────────────────────────

    @Test
    fun `no NaN output for 1000 consecutive updates`() {
        val kf = KalmanFilter1D()
        val random = java.util.Random(123L)
        repeat(1000) {
            val estimate = kf.update(random.nextGaussian() * 0.001 - 23.55, dt)
            assertFalse(estimate.isNaN(), "Got NaN at iteration $it")
            assertFalse(estimate.isInfinite(), "Got Inf at iteration $it")
        }
    }

    // ── velocity estimate ─────────────────────────────────────────────────

    @Test
    fun `velocity estimate is non-zero when signal is moving`() {
        val kf = KalmanFilter1D()
        // Feed a linearly increasing signal
        var x = 0.0
        repeat(50) {
            kf.update(x, dt)
            x += 0.001
        }
        assertTrue(
            abs(kf.currentVelocity()) > 0.0,
            "Expected non-zero velocity for moving signal"
        )
    }

    @Test
    fun `velocity estimate is near zero for constant signal`() {
        val kf = KalmanFilter1D()
        repeat(100) { kf.update(0.5, dt) }
        assertTrue(
            abs(kf.currentVelocity()) < 0.01,
            "Expected near-zero velocity for constant signal, got ${kf.currentVelocity()}"
        )
    }

    // ── reset ─────────────────────────────────────────────────────────────

    @Test
    fun `reset causes next update to return measurement directly`() {
        val kf = KalmanFilter1D()
        repeat(50) { kf.update(-23.55, dt) }
        kf.reset()
        val estimate = kf.update(99.99, dt)
        assertEquals(99.99, estimate, 1e-9)
    }

    @Test
    fun `velocity is zero after reset`() {
        val kf = KalmanFilter1D()
        repeat(50) { kf.update(it.toDouble() * 0.01, dt) }
        kf.reset()
        assertEquals(0.0, kf.currentVelocity(), 1e-9)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun variance(data: List<Double>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean) * (it - mean) } / data.size
    }
}
