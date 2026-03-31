package com.ghostpin.engine.noise

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile

/**
 * Layered noise model — the full composition pipeline for GPS simulation.
 *
 * Composes all components in sequence per frame:
 * 1. **Multipath** (internally uses OU for correlated base noise)
 * 2. **Tunnel** — signal loss with position freeze and re-acquisition
 * 3. **Quantization** — finite receiver resolution
 * 4. **Sensor Coherence** — speed/bearing/accuracy/altitude consistency
 *
 * Bug fixes in this revision:
 *  - Tunnel events now correctly freeze the last known position instead of
 *    allowing the interpolated position to continue advancing. Real GPS receivers
 *    do not emit new position updates during signal loss.
 *  - `cos(lat)` is now protected against division-by-zero at polar latitudes (±90°).
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
    /**
     * Fix (🔴): Track the last successfully emitted location so it can be
     * returned (frozen) during tunnel/signal-loss events.
     *
     * Without this, tunnel events only degraded `accuracy` but kept advancing
     * `lat`/`lng` from the RouteInterpolator — producing a stream of precise
     * positions with high accuracy values that is internally contradictory.
     */
    private var lastKnownLocation: MockLocation? = null

    companion object {
        /**
         * Factory: create a fully-configured [LayeredNoiseModel] from a profile.
         * Uses seeded RNG for reproducible simulations.
         */
        fun fromProfile(
            profile: MovementProfile,
            seed: Long? = null,
        ): LayeredNoiseModel {
            val random = if (seed != null) java.util.Random(seed) else java.util.Random()

            val ou =
                OrnsteinUhlenbeckNoiseGenerator(
                    theta = profile.theta,
                    sigma = profile.sigma,
                    random = random,
                )

            val multipath =
                MultipathNoiseModel(
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
     * @param raw           The clean/interpolated location before noise.
     * @param deltaTimeSec  Time since last frame (seconds).
     * @return Noisy, coherent [MockLocation] ready for injection.
     */
    fun applyToLocation(
        raw: MockLocation,
        deltaTimeSec: Double
    ): MockLocation {
        // ── 1. Evaluate tunnel state ─────────────────────────────────────────
        val tunnelEvent = tunnel.update(deltaTimeSec)

        if (tunnelEvent?.suppressPosition == true) {
            // Fix (🔴): Return the last known (frozen) position, NOT the current
            // interpolated position from RouteInterpolator.
            //
            // Before this fix, `raw.copy(accuracy = ...)` was returned, which kept
            // advancing lat/lng through the route while claiming signal was lost.
            // Real GPS hardware does not emit new positions during signal loss —
            // the last received fix is retained with degraded accuracy.
            val frozen = lastKnownLocation ?: raw
            return frozen.copy(
                accuracy = tunnelEvent.accuracyOverride,
                // Update timing fields so the Location object stays monotonically increasing
                timestampMs = raw.timestampMs,
                elapsedRealtimeNanos = raw.elapsedRealtimeNanos,
            )
        }

        // ── 2. Apply correlated noise + multipath ────────────────────────────
        val noise = multipath.sample(deltaTimeSec)

        // Convert noise displacement from meters to degrees (approximate equirectangular)
        val metersToDegreesLat = 1.0 / 111_320.0

        // Fix (🟠): Protect against division by zero at polar latitudes (lat = ±90°).
        // Math.cos(±π/2) = 0 would produce metersToDegreesLng = Infinity, corrupting
        // longitude with NaN noise values. coerceAtLeast(0.001) caps the effective
        // latitude at ~89.9°, which is well beyond any realistic usage.
        val cosLat = Math.cos(Math.toRadians(raw.lat)).coerceAtLeast(0.001)
        val metersToDegreesLng = 1.0 / (111_320.0 * cosLat)

        val noisyLat = raw.lat + noise.lat * metersToDegreesLat
        val noisyLng = raw.lng + noise.lng * metersToDegreesLng

        // ── 3. Quantize coordinates ──────────────────────────────────────────
        val qLat = quantization.quantize(noisyLat)
        val qLng = quantization.quantize(noisyLng)

        // ── 4. Dynamic accuracy ──────────────────────────────────────────────
        val baseAccuracy = raw.accuracy
        val jumpPenalty = if (noise.isJump) baseAccuracy * 0.5f else 0f
        val speedPenalty = (raw.speed / profile.maxSpeedMs.toFloat()) * 2f
        val tunnelPenalty = tunnelEvent?.accuracyOverride ?: 0f

        val dynamicAccuracy =
            (baseAccuracy + jumpPenalty + speedPenalty + tunnelPenalty)
                .coerceIn(2f, 50f)

        val noisyLocation =
            raw.copy(
                lat = qLat,
                lng = qLng,
                accuracy = dynamicAccuracy,
            )

        // ── 5. Sensor coherence filter ───────────────────────────────────────
        val result = coherence.apply(noisyLocation, deltaTimeSec, noise.isJump)

        // Update freeze reference for next tunnel event
        lastKnownLocation = result

        return result
    }

    /**
     * Reset all components and clear the frozen position — for a new simulation.
     */
    fun reset() {
        multipath.reset()
        tunnel.reset()
        coherence.reset()
        lastKnownLocation = null
    }
}
