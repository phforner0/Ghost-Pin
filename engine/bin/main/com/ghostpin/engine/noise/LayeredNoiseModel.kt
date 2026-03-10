package com.ghostpin.engine.noise

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile

/**
 * Layered noise model — the full composition pipeline for GPS simulation.
 *
 * Composes all three audit-corrected components in sequence per frame:
 * 1. **Multipath** (internally uses OU for correlated base noise) — Gap 1 + Gap 2
 * 2. **Tunnel** — signal loss with re-acquisition
 * 3. **Quantization** — finite receiver resolution
 * 4. **Sensor Coherence** — speed/bearing/accuracy/altitude consistency — Gap 3
 *
 * @param profile Active movement profile.
 * @param multipath Multipath noise model (wraps OU generator).
 * @param tunnel Tunnel/signal-loss model.
 * @param quantization Coordinate quantizer.
 * @param coherence Sensor coherence post-processor.
 */
class LayeredNoiseModel(
    val profile: MovementProfile,
    val multipath: MultipathNoiseModel,
    val tunnel: TunnelNoiseModel,
    val quantization: QuantizationNoise,
    val coherence: SensorCoherenceFilter,
) {
    companion object {
        /**
         * Factory: create a fully-configured LayeredNoiseModel from a profile.
         * Uses seeded RNG for reproducible simulations.
         */
        fun fromProfile(
            profile: MovementProfile,
            seed: Long? = null,
        ): LayeredNoiseModel {
            val random = if (seed != null) java.util.Random(seed) else java.util.Random()

            val ou = OrnsteinUhlenbeckNoiseGenerator(
                theta = profile.theta,
                sigma = profile.sigma,
                random = random,
            )

            val multipath = MultipathNoiseModel(
                ouGenerator = ou,
                pMultipath = profile.pMultipath,
                laplaceScale = profile.laplaceScale,
                random = random,
            )

            val tunnel = TunnelNoiseModel(profile, random)
            val quantization = QuantizationNoise(profile.quantizationDecimals)
            val coherence = SensorCoherenceFilter(profile)

            return LayeredNoiseModel(profile, multipath, tunnel, quantization, coherence)
        }
    }

    /**
     * Apply the full noise pipeline to a raw mock location.
     *
     * @param raw The clean/interpolated location before noise.
     * @param deltaTimeSec Time since last frame (seconds).
     * @return Noisy, coherent [MockLocation] ready for injection.
     */
    fun applyToLocation(raw: MockLocation, deltaTimeSec: Double): MockLocation {
        // --- 1. Check tunnel state ---
        val tunnelEvent = tunnel.update(deltaTimeSec)

        if (tunnelEvent?.suppressPosition == true) {
            // In tunnel: freeze position, degrade accuracy massively
            return raw.copy(
                accuracy = tunnelEvent.accuracyOverride,
            )
        }

        // --- 2. Apply correlated noise + multipath ---
        val noise = multipath.sample(deltaTimeSec)

        // Convert noise from meters to degrees (approximate)
        val metersToDegreesLat = 1.0 / 111_320.0
        val metersToDegreesLng = 1.0 / (111_320.0 * Math.cos(Math.toRadians(raw.lat)))

        val noisyLat = raw.lat + noise.lat * metersToDegreesLat
        val noisyLng = raw.lng + noise.lng * metersToDegreesLng

        // --- 3. Quantize coordinates ---
        val qLat = quantization.quantize(noisyLat)
        val qLng = quantization.quantize(noisyLng)

        // --- 4. Dynamic accuracy ---
        val baseAccuracy = raw.accuracy
        val jumpPenalty = if (noise.isJump) baseAccuracy * 0.5f else 0f
        val speedPenalty = (raw.speed / profile.maxSpeedMs.toFloat()) * 2f
        val tunnelPenalty = tunnelEvent?.accuracyOverride ?: 0f

        val dynamicAccuracy = (baseAccuracy + jumpPenalty + speedPenalty + tunnelPenalty)
            .coerceIn(2f, 50f)

        val noisyLocation = raw.copy(
            lat = qLat,
            lng = qLng,
            accuracy = dynamicAccuracy,
        )

        // --- 5. Sensor coherence filter ---
        return coherence.apply(noisyLocation, deltaTimeSec, noise.isJump)
    }

    /**
     * Reset all components — for new simulation.
     */
    fun reset() {
        multipath.reset()
        tunnel.reset()
        coherence.reset()
    }
}
