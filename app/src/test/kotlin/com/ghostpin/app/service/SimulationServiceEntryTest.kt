package com.ghostpin.app.service

import android.content.Intent
import android.net.Uri
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimulationServiceEntryTest {

    @Test
    fun `classifyImmediateServiceAction handles null and shortcut actions`() {
        assertTrue(classifyImmediateServiceAction(null) is ImmediateServiceAction.NullIntentStop)
        assertTrue(
            classifyImmediateServiceAction(Intent().apply { action = SimulationService.ACTION_START_LAST_CONFIG })
                is ImmediateServiceAction.StartLastConfig
        )
        assertTrue(
            classifyImmediateServiceAction(Intent().apply { action = SimulationService.ACTION_START_LAST_FAVORITE })
                is ImmediateServiceAction.StartLastFavorite
        )
        assertTrue(
            classifyImmediateServiceAction(Intent().apply { action = SimulationService.ACTION_SET_ROUTE; data = Uri.parse("content://x") })
                is ImmediateServiceAction.SetRoute
        )
    }

    @Test
    fun `buildUpdatedProfileConfig keeps existing launch context while swapping profile`() {
        val current =
            SimulationConfig(
                profileName = "Car",
                profileLookupKey = "car-id",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                routeId = "route-1",
                appMode = com.ghostpin.core.model.AppMode.WAYPOINTS,
                waypointPauseSec = 4.0,
                speedRatio = 0.7,
                frequencyHz = 12,
                repeatPolicy = RepeatPolicy.LOOP_N,
                repeatCount = 3,
            )

        val updated = buildUpdatedProfileConfig(current, MovementProfile.PEDESTRIAN, "ped-id", defaultFrequency = 5)

        assertEquals(MovementProfile.PEDESTRIAN.name, updated.profileName)
        assertEquals("ped-id", updated.profileLookupKey)
        assertEquals(current.startLat, updated.startLat, 0.0)
        assertEquals(current.routeId, updated.routeId)
        assertEquals(current.frequencyHz, updated.frequencyHz)
        assertEquals(current.repeatCount, updated.repeatCount)
    }

    @Test
    fun `resolve shortcut decisions start valid config and stop on errors`() {
        val config = SimulationConfig(profileName = "Car", profileLookupKey = "car", startLat = 1.0, startLng = 2.0)
        val favoriteDecision = resolveFavoriteShortcutDecision(
            SimulationRepository.FavoriteResolution.Valid(
                config = config,
                favorite = fakeFavorite,
            )
        )
        val favoriteError = resolveFavoriteShortcutDecision(
            SimulationRepository.FavoriteResolution.Invalid(
                reason = "No favorites saved yet.",
                fallbackConfig = null,
            )
        )
        val lastConfigDecision = resolveLastConfigShortcutDecision(
            currentConfig = config,
            validation = SimulationRepository.ConfigValidation.Valid(config),
        )
        val lastConfigError = resolveLastConfigShortcutDecision(
            currentConfig = null,
            validation = null,
        )

        assertTrue(favoriteDecision is ShortcutStartDecision.Start)
        assertTrue(favoriteError is ShortcutStartDecision.ErrorAndStop)
        assertTrue(lastConfigDecision is ShortcutStartDecision.Start)
        assertTrue(lastConfigError is ShortcutStartDecision.ErrorAndStop)
        assertEquals("No recent simulation configuration available.", (lastConfigError as ShortcutStartDecision.ErrorAndStop).message)
    }

    private companion object {
        val fakeFavorite =
            FavoriteSimulationEntity(
                id = "fav-1",
                name = "Favorite",
                profileName = "Car",
                profileIdOrName = "car",
                routeId = null,
                startLat = 1.0,
                startLng = 2.0,
                endLat = 3.0,
                endLng = 4.0,
                appMode = com.ghostpin.core.model.AppMode.CLASSIC.name,
                waypointsJson = "[]",
                waypointPauseSec = 0.0,
                speedRatio = 1.0,
                frequencyHz = 5,
                repeatPolicy = RepeatPolicy.NONE.name,
                repeatCount = 1,
                createdAtMs = 0L,
                updatedAtMs = 0L,
            )
    }
}
