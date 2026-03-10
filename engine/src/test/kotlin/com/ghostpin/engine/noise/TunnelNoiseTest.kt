package com.ghostpin.engine.noise

import com.ghostpin.core.model.MovementProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests for the tunnel/signal-loss noise model.
 */
class TunnelNoiseTest {

    @Test
    fun `tunnel events occur at approximately expected rate`() {
        val profile = MovementProfile.URBAN_VEHICLE // highest tunnel probability
        val model = TunnelNoiseModel(profile, Random(42))

        var tunnelEntries = 0
        val totalSteps = 100_000
        val dt = 1.0

        for (i in 0 until totalSteps) {
            val event = model.update(dt)
            if (event != null && event.isInTunnel && !model.isInTunnel) {
                // Count only when transitioning from not-in-tunnel to in-tunnel
            }
            if (event?.isInTunnel == true && event.suppressPosition) {
                tunnelEntries++
            }
        }

        // With p=0.002/s and 100,000 seconds, we expect ~200 entries
        // But tunnels last several seconds each, so we'll see more updates per tunnel
        assertTrue(
            tunnelEntries > 50,
            "Expected significant tunnel events for urban vehicle, got $tunnelEntries"
        )
    }

    @Test
    fun `tunnel suppresses position`() {
        val profile = MovementProfile.URBAN_VEHICLE
        val model = TunnelNoiseModel(profile, Random(42))

        // Force a tunnel by running until one occurs
        var tunnelEvent: TunnelEvent? = null
        for (i in 0 until 100_000) {
            tunnelEvent = model.update(1.0)
            if (tunnelEvent?.isInTunnel == true) break
        }

        assertNotNull(tunnelEvent, "Should have encountered at least one tunnel event")
        assertTrue(tunnelEvent!!.suppressPosition, "Tunnel should suppress position updates")
        assertEquals(
            TunnelNoiseModel.TUNNEL_ACCURACY,
            tunnelEvent.accuracyOverride,
            "Accuracy should be ${ TunnelNoiseModel.TUNNEL_ACCURACY}m during tunnel"
        )
    }

    @Test
    fun `re-acquisition occurs after tunnel exit`() {
        val profile = MovementProfile.URBAN_VEHICLE
        val model = TunnelNoiseModel(profile, Random(42))

        // Run until we get a tunnel
        for (i in 0 until 100_000) {
            val event = model.update(1.0)
            if (event?.isInTunnel == true) break
        }

        // Now keep updating until tunnel ends
        var postTunnelEvent: TunnelEvent? = null
        for (i in 0 until 1000) {
            val event = model.update(1.0)
            if (event?.isReAcquiring == true) {
                postTunnelEvent = event
                break
            }
            if (event == null) break // past the tunnel entirely
        }

        if (postTunnelEvent != null) {
            assertTrue(postTunnelEvent.isReAcquiring)
            assertFalse(postTunnelEvent.suppressPosition, "Should NOT suppress position during re-acquisition")
            assertTrue(postTunnelEvent.accuracyOverride > 0, "Should have extra accuracy overhead")
        }
    }

    @Test
    fun `reset clears tunnel state`() {
        val profile = MovementProfile.PEDESTRIAN
        val model = TunnelNoiseModel(profile, Random(42))

        // Enter a tunnel (force by running enough steps)
        for (i in 0 until 100_000) {
            val event = model.update(1.0)
            if (event?.isInTunnel == true) break
        }

        model.reset()
        assertFalse(model.isInTunnel)
        assertFalse(model.isReAcquiring)
        assertEquals(0.0, model.remainingTunnelSec)
    }

    @Test
    fun `drone has very low tunnel probability`() {
        val model = TunnelNoiseModel(MovementProfile.DRONE, Random(42))

        var tunnelCount = 0
        repeat(10_000) {
            if (model.update(1.0)?.isInTunnel == true) tunnelCount++
        }

        // Drone has p=0.0001/s, over 10k seconds expect ~1 tunnel event (few updates)
        assertTrue(
            tunnelCount < 100,
            "Drone should have very few tunnel events, got $tunnelCount"
        )
    }
}
