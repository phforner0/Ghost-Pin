package com.ghostpin.engine.noise

import com.ghostpin.core.model.MovementProfile
import java.util.Random
import kotlin.math.exp

/**
 * Simulates temporary GPS signal loss events (e.g., tunnels, underpasses,
 * dense canopy) with gradual signal re-acquisition.
 *
 * During a tunnel event:
 * - Accuracy degrades to [TUNNEL_ACCURACY] (50m)
 * - No position updates are emitted (lat/lng frozen)
 *
 * After a tunnel event:
 * - Accuracy recovers gradually over [RE_ACQUISITION_STEPS] frames
 * - First fix post-tunnel may have elevated error
 *
 * Tunnel duration is sampled from a LogNormal distribution to match
 * real-world variability (short underpasses to long tunnels).
 *
 * @param profile Movement profile providing tunnel probability/duration params.
 * @param random RNG for reproducible testing.
 */
class TunnelNoiseModel(
    private val profile: MovementProfile,
    private val random: Random = Random(),
) {
    companion object {
        /** Accuracy reported during total signal loss. */
        const val TUNNEL_ACCURACY: Float = 50f

        /** Number of frames for gradual re-acquisition after tunnel exit. */
        const val RE_ACQUISITION_STEPS: Int = 5

        /** Accuracy added to first fix after tunnel (meters). */
        const val POST_TUNNEL_EXTRA_ACCURACY: Float = 20f
    }

    /** Remaining seconds of the current tunnel event (0 = not in tunnel). */
    var remainingTunnelSec: Double = 0.0
        private set

    /** Steps remaining in re-acquisition phase after tunnel exit. */
    var reAcquisitionStepsLeft: Int = 0
        private set

    /** Whether currently inside a tunnel (no position updates). */
    val isInTunnel: Boolean get() = remainingTunnelSec > 0.0

    /** Whether in the post-tunnel re-acquisition phase. */
    val isReAcquiring: Boolean get() = reAcquisitionStepsLeft > 0

    /**
     * Called once per simulation frame to update tunnel state.
     *
     * @param deltaTimeSec Time since last frame.
     * @return A [TunnelEvent] if currently in tunnel or re-acquiring, null otherwise.
     */
    fun update(deltaTimeSec: Double): TunnelEvent? {
        // Currently in tunnel — count down
        if (remainingTunnelSec > 0.0) {
            remainingTunnelSec -= deltaTimeSec
            if (remainingTunnelSec <= 0.0) {
                // Exiting tunnel — start re-acquisition
                remainingTunnelSec = 0.0
                reAcquisitionStepsLeft = RE_ACQUISITION_STEPS
                return TunnelEvent(
                    isInTunnel = false,
                    isReAcquiring = true,
                    accuracyOverride = TUNNEL_ACCURACY,
                    suppressPosition = false,
                )
            }
            return TunnelEvent(
                isInTunnel = true,
                isReAcquiring = false,
                accuracyOverride = TUNNEL_ACCURACY,
                suppressPosition = true,
            )
        }

        // In re-acquisition phase — gradually reduce extra accuracy
        if (reAcquisitionStepsLeft > 0) {
            val fraction = reAcquisitionStepsLeft.toFloat() / RE_ACQUISITION_STEPS
            reAcquisitionStepsLeft--
            return TunnelEvent(
                isInTunnel = false,
                isReAcquiring = true,
                accuracyOverride = POST_TUNNEL_EXTRA_ACCURACY * fraction,
                suppressPosition = false,
            )
        }

        // Not in tunnel — maybe start one
        if (random.nextDouble() < profile.tunnelProbabilityPerSec * deltaTimeSec) {
            remainingTunnelSec =
                sampleLogNormal(
                    mu = profile.tunnelDurationMeanSec,
                    sigma = profile.tunnelDurationSigmaSec,
                )
            return TunnelEvent(
                isInTunnel = true,
                isReAcquiring = false,
                accuracyOverride = TUNNEL_ACCURACY,
                suppressPosition = true,
            )
        }

        return null
    }

    /**
     * Reset tunnel state — for new simulation.
     */
    fun reset() {
        remainingTunnelSec = 0.0
        reAcquisitionStepsLeft = 0
    }

    /**
     * Sample from LogNormal distribution.
     * If X ~ N(μ, σ²), then exp(X) ~ LogNormal(μ, σ²).
     */
    private fun sampleLogNormal(
        mu: Double,
        sigma: Double
    ): Double {
        val normal = random.nextGaussian() * sigma + mu
        return exp(normal)
    }
}

/**
 * Represents the state of a tunnel/signal-loss event.
 */
data class TunnelEvent(
    /** True if currently inside a tunnel (total signal loss). */
    val isInTunnel: Boolean,
    /** True if in the post-tunnel re-acquisition phase. */
    val isReAcquiring: Boolean,
    /** Accuracy override to apply (meters). Added to base accuracy. */
    val accuracyOverride: Float,
    /** If true, suppress position updates (freeze lat/lng). */
    val suppressPosition: Boolean,
)
