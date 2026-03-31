package com.ghostpin.engine.noise

import com.ghostpin.core.model.NoiseVector
import java.util.Random
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign

/**
 * Multipath urban noise model — mixture of OU base noise + Laplace heavy-tail jumps.
 *
 * Fixes Gap 2 from the audit: real GPS in dense urban environments exhibits
 * non-Gaussian error distributions with heavy tails caused by signal reflection
 * off building facades (multipath). A pure Gaussian model underestimates large
 * sporadic errors and overestimates small frequent ones.
 *
 * With probability [pMultipath], a Laplace-distributed jump is added to the
 * OU base noise. The Laplace distribution has heavier tails than Gaussian,
 * producing realistic kurtosis > 3 in the combined error distribution.
 *
 * Laplace PDF: P(x) = (1/2b) · exp(-|x - μ| / b)
 *
 * @param ouGenerator The underlying Ornstein-Uhlenbeck correlated noise source.
 * @param pMultipath Probability of a multipath jump per sample (0..1).
 * @param laplaceScale Laplace b-parameter controlling jump magnitude (meters).
 * @param random RNG for reproducible testing.
 */
class MultipathNoiseModel(
    private val ouGenerator: OrnsteinUhlenbeckNoiseGenerator,
    private val pMultipath: Double,
    private val laplaceScale: Double,
    private val random: Random = Random(),
) {
    /** Whether the last sample was a multipath jump — used for accuracy adjustment. */
    var lastWasJump: Boolean = false
        private set

    init {
        require(pMultipath in 0.0..1.0) { "pMultipath must be in [0, 1], got $pMultipath" }
        require(laplaceScale > 0) { "laplaceScale must be positive, got $laplaceScale" }
    }

    /**
     * Sample noise with potential multipath jump.
     *
     * @param deltaTimeSec Time since last sample (seconds).
     * @return [NoiseVector] with combined OU + optional Laplace displacement.
     */
    fun sample(deltaTimeSec: Double): NoiseVector {
        val (baseLat, baseLng) = ouGenerator.next(deltaTimeSec)

        if (random.nextDouble() < pMultipath) {
            val jumpLat = laplaceSample(scale = laplaceScale)
            val jumpLng = laplaceSample(scale = laplaceScale)
            lastWasJump = true
            return NoiseVector(
                lat = baseLat + jumpLat,
                lng = baseLng + jumpLng,
                isJump = true,
            )
        }

        lastWasJump = false
        return NoiseVector(lat = baseLat, lng = baseLng, isJump = false)
    }

    /**
     * Reset the underlying OU generator and jump state.
     */
    fun reset() {
        ouGenerator.reset()
        lastWasJump = false
    }

    /**
     * Sample from the Laplace distribution using inverse CDF.
     *
     * CDF⁻¹(p) = μ - b · sign(p - 0.5) · ln(1 - 2|p - 0.5|)
     *
     * @param mu Location parameter (default 0).
     * @param scale Scale parameter b (controls spread).
     */
    private fun laplaceSample(
        mu: Double = 0.0,
        scale: Double
    ): Double {
        val u = random.nextDouble() - 0.5
        return mu - scale * sign(u) * ln(1.0 - 2.0 * abs(u))
    }
}
