package com.ghostpin.app.service

import android.os.Build
import android.content.ContextWrapper
import android.content.Intent
import android.service.quicksettings.Tile
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.core.model.MockLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GhostPinQsTileTest {

    @Test
    fun `renderTileModel shows active state with profile while running`() {
        val model = renderTileModel(
            state = SimulationState.Running(
                currentLocation = MockLocation(1.0, 2.0),
                profileName = "Car",
                progressPercent = 0.3f,
                elapsedTimeSec = 12,
                frameCount = 40,
            ),
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

        assertEquals(Tile.STATE_ACTIVE, model.state)
        assertEquals("Car", model.subtitle)
        assertEquals("GhostPin", model.label)
    }

    @Test
    fun `renderTileModel shows paused subtitle while paused`() {
        val model = renderTileModel(
            state = SimulationState.Paused(
                lastLocation = MockLocation(1.0, 2.0),
                profileName = "Bike",
                progressPercent = 0.5f,
                currentLap = 1,
                totalLapsLabel = "1",
                lapProgressPercent = 0.5f,
                direction = 1,
                elapsedTimeSec = 33,
            ),
            sdkInt = Build.VERSION_CODES.TIRAMISU,
        )

        assertEquals(Tile.STATE_ACTIVE, model.state)
        assertEquals("Paused", model.subtitle)
    }

    @Test
    fun `renderTileModel hides subtitle below android q`() {
        val model = renderTileModel(
            state = SimulationState.Running(
                currentLocation = MockLocation(1.0, 2.0),
                profileName = "Car",
                progressPercent = 0.1f,
                elapsedTimeSec = 2,
                frameCount = 3,
            ),
            sdkInt = Build.VERSION_CODES.P,
        )

        assertEquals(Tile.STATE_ACTIVE, model.state)
        assertEquals(null, model.subtitle)
    }

    @Test
    fun `renderTileModel is inactive for idle and error states`() {
        val idleModel = renderTileModel(SimulationState.Idle, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val errorModel = renderTileModel(SimulationState.Error("Boom"), Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        assertEquals(Tile.STATE_INACTIVE, idleModel.state)
        assertEquals(null, idleModel.subtitle)
        assertEquals(Tile.STATE_INACTIVE, errorModel.state)
        assertEquals(null, errorModel.subtitle)
    }

    @Test
    fun `shouldStopCurrentSimulation is true for active states only`() {
        assertTrue(shouldStopCurrentSimulation(
            SimulationState.Running(
                currentLocation = MockLocation(1.0, 2.0),
                profileName = "Car",
                progressPercent = 0.1f,
                elapsedTimeSec = 1,
                frameCount = 1,
            )
        ))

        assertTrue(shouldStopCurrentSimulation(
            SimulationState.Paused(
                lastLocation = MockLocation(1.0, 2.0),
                profileName = "Car",
                progressPercent = 0.1f,
                currentLap = 1,
                totalLapsLabel = "1",
                lapProgressPercent = 0.1f,
                direction = 1,
                elapsedTimeSec = 1,
            )
        ))

        assertEquals(false, shouldStopCurrentSimulation(SimulationState.Idle))
    }

    @Test
    fun `resolveClickDecision starts favorite when config is valid`() {
        val config = SimulationConfig(profileName = "Car", startLat = 1.0, startLng = 2.0)
        val resolution = SimulationRepository.FavoriteResolution.Valid(
            config = config,
            favorite = fakeFavorite,
        )

        val decision = resolveClickDecision(resolution)

        assertTrue(decision is GhostPinQsTileClickDecision.StartFavorite)
        assertEquals(config, (decision as GhostPinQsTileClickDecision.StartFavorite).config)
    }

    @Test
    fun `resolveClickDecision opens app when favorite is invalid`() {
        val resolution = SimulationRepository.FavoriteResolution.Invalid(
            reason = "No favorites saved yet.",
            fallbackConfig = null,
        )

        val decision = resolveClickDecision(resolution)

        assertTrue(decision is GhostPinQsTileClickDecision.OpenApp)
        assertEquals("No favorites saved yet.", (decision as GhostPinQsTileClickDecision.OpenApp).reason)
    }

    @Test
    fun `buildStopIntent targets simulation service stop action`() {
        val context = ContextWrapper(RuntimeEnvironment.getApplication())

        val intent = buildStopIntent(context)

        assertEquals(SimulationService.ACTION_STOP, intent.action)
        assertEquals(Intent(context, SimulationService::class.java).component, intent.component)
    }

    private companion object {
        val fakeFavorite = com.ghostpin.app.data.db.FavoriteSimulationEntity(
            id = "fav-1",
            name = "Favorite",
            profileName = "Car",
            profileIdOrName = "Car",
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
            repeatPolicy = com.ghostpin.engine.interpolation.RepeatPolicy.NONE.name,
            repeatCount = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
    }
}
