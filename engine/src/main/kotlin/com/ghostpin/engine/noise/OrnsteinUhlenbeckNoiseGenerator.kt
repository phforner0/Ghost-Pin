package com.ghostpin.engine.noise

import java.util.Random
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Ornstein-Uhlenbeck process for temporally-correlated GPS noise.
 *
 * Replaces the i.i.d. Box-Muller model that was flagged in the audit (Gap 1).
 * Real GPS errors exhibit strong temporal autocorrelation: the error at t+1
 * depends on the error at t. The OU process naturally produces this behavior
 * with exponential decay of autocorrelation.
 *
 * Discrete equation:
 * ```
 * noise(t+1) = mu + (noise(t) - mu) * exp(-θ·Δt) + σ·√(1 - exp(-2θ·Δt)) · ε
 * ```
 * where ε ~ N(0,1) is the only independent Gaussian sample per step.
 *
 * @param theta Mean reversion speed (s⁻¹). Higher θ = faster decorrelation.
 * @param sigma Volatility (meters equivalent). Controls the amplitude of noise.
 * @param mu Long-run mean. Default 0.0 — no systematic bias.
 * @param random RNG instance for reproducible testing.
 */
class OrnsteinUhlenbeckNoiseGenerator(
    private val theta: Double,
    private val sigma: Double,
    private val mu: Double = 0.0,
    private val random: Random = Random(),
) {
    /** Current state for latitude noise. */
    var currentLat: Double = 0.0
        private set

    /** Current state for longitude noise. */
    var currentLng: Double = 0.0
        private set

    /**
     * Generate the next correlated noise sample for both lat and lng.
     *
     * @param deltaTimeSec Time elapsed since last sample (seconds).
     * @return Pair of (latNoise, lngNoise) in meters-equivalent.
     */
    fun next(deltaTimeSec: Double): Pair<Double, Double> {
        require(deltaTimeSec > 0) { "deltaTimeSec must be positive, got $deltaTimeSec" }

        val decay = exp(-theta * deltaTimeSec)
        val stdDev = sigma * sqrt(1.0 - decay * decay)

        currentLat = mu + decay * (currentLat - mu) + stdDev * random.nextGaussian()
        currentLng = mu + decay * (currentLng - mu) + stdDev * random.nextGaussian()

        return Pair(currentLat, currentLng)
    }

    /**
     * Reset the noise state to zero.
     * Called when starting a new simulation or changing profiles.
     */
    fun reset() {
        currentLat = 0.0
        currentLng = 0.0
    }
}
