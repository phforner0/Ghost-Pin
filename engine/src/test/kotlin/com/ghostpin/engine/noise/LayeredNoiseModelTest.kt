package com.ghostpin.engine.noise

import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class LayeredNoiseModelTest {

    @Test
    fun `applyToLocation preserves timing fields and finite coordinates`() {
        val model = LayeredNoiseModel.fromProfile(MovementProfile.PEDESTRIAN, seed = 123L)
        val raw = MockLocation(
            lat = -23.55052,
            lng = -46.63331,
            altitude = 12.0,
            speed = 1.5f,
            bearing = 42f,
            accuracy = 6f,
            timestampMs = 123456789L,
            elapsedRealtimeNanos = 987654321L,
        )

        val result = model.applyToLocation(raw, deltaTimeSec = 0.2)

        assertTrue(result.lat.isFinite())
        assertTrue(result.lng.isFinite())
        assertEquals(raw.timestampMs, result.timestampMs)
        assertEquals(raw.elapsedRealtimeNanos, result.elapsedRealtimeNanos)
    }

    @Test
    fun `tunnel event freezes last known position`() {
        val tunnelProfile = MovementProfile.PEDESTRIAN.copy(
            tunnelProbabilityPerSec = 1.0,
            tunnelDurationMeanSec = 0.0,
            tunnelDurationSigmaSec = 0.0,
        )
        val random = ControlledRandom(
            doubles = doubleArrayOf(1.0, 1.0, 0.0),
            gaussians = doubleArrayOf(0.0, 0.0, 0.0),
        )
        val model = LayeredNoiseModel(
            profile = tunnelProfile,
            multipath = MultipathNoiseModel(
                ouGenerator = OrnsteinUhlenbeckNoiseGenerator(
                    theta = tunnelProfile.theta,
                    sigma = tunnelProfile.sigma,
                    random = random,
                ),
                pMultipath = tunnelProfile.pMultipath,
                laplaceScale = tunnelProfile.laplaceScale,
                random = random,
            ),
            tunnel = TunnelNoiseModel(tunnelProfile, random),
            quantization = QuantizationNoise(tunnelProfile.quantizationDecimals),
            coherence = SensorCoherenceFilter(tunnelProfile),
        )

        val first = model.applyToLocation(
            MockLocation(
                lat = -23.0,
                lng = -46.0,
                speed = 1.0f,
                timestampMs = 1L,
                elapsedRealtimeNanos = 1L,
            ),
            deltaTimeSec = 0.1,
        )

        val second = model.applyToLocation(
            MockLocation(
                lat = -22.0,
                lng = -45.0,
                speed = 1.0f,
                timestampMs = 2L,
                elapsedRealtimeNanos = 2L,
            ),
            deltaTimeSec = 0.1,
        )

        assertEquals(first.lat, second.lat, 1e-9)
        assertEquals(first.lng, second.lng, 1e-9)
        assertEquals(2L, second.timestampMs)
        assertEquals(2L, second.elapsedRealtimeNanos)
        assertTrue(second.accuracy >= TunnelNoiseModel.TUNNEL_ACCURACY)
    }

    private class ControlledRandom(
        private val doubles: DoubleArray,
        private val gaussians: DoubleArray,
    ) : Random() {
        private var doubleIndex = 0
        private var gaussianIndex = 0

        override fun nextDouble(): Double {
            val value = doubles.getOrElse(doubleIndex) { 1.0 }
            doubleIndex++
            return value
        }

        override fun nextGaussian(): Double {
            val value = gaussians.getOrElse(gaussianIndex) { 0.0 }
            gaussianIndex++
            return value
        }
    }
}
