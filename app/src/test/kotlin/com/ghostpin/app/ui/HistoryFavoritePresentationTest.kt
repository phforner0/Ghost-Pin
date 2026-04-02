package com.ghostpin.app.ui

import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFavoritePresentationTest {
    @Test
    fun `favorite name suggestion uses mode when route is direct`() {
        val config =
            SimulationConfig(
                profileName = "Car",
                startLat = -23.0,
                startLng = -46.0,
            )

        assertEquals("Car · Classic", config.favoriteNameSuggestion())
    }

    @Test
    fun `favorite name suggestion prefers route summary when route is richer`() {
        val config =
            SimulationConfig(
                profileName = "Bike",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                appMode = AppMode.GPX,
                waypoints =
                    listOf(
                        Waypoint(-23.0, -46.0),
                        Waypoint(-22.0, -43.0),
                        Waypoint(-21.0, -42.0),
                    ),
            )

        assertEquals("Bike · Rota importada (3 pts)", config.favoriteNameSuggestion())
    }

    @Test
    fun `favorite summary line describes mode and route`() {
        val favorite =
            FavoriteSimulationEntity(
                id = "favorite-1",
                name = "Trip",
                profileName = "Walk",
                profileIdOrName = "Walk",
                routeId = null,
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                appMode = AppMode.WAYPOINTS.name,
                waypointsJson =
                    SimulationConfig.serializeWaypoints(
                        listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                    ),
                waypointPauseSec = 0.0,
                speedRatio = 1.0,
                frequencyHz = 5,
                repeatPolicy = RepeatPolicy.NONE.name,
                repeatCount = 1,
                createdAtMs = 0L,
                updatedAtMs = 0L,
            )

        assertEquals("Waypoints · 2 waypoints", favorite.summaryLine())
    }

    @Test
    fun `history summary and status labels are user friendly`() {
        val history =
            SimulationHistoryEntity(
                id = "history-1",
                profileName = "Car",
                profileIdOrName = "builtin_car",
                routeId = "route-1",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                appMode = AppMode.CLASSIC.name,
                waypointsJson = "[]",
                waypointPauseSec = 0.0,
                speedRatio = 1.0,
                frequencyHz = 5,
                repeatPolicy = RepeatPolicy.NONE.name,
                repeatCount = 1,
                startedAtMs = 0L,
                endedAtMs = 60_000L,
                durationMs = 60_000L,
                avgSpeedMs = 10.25,
                distanceMeters = 615.0,
                resultStatus = "COMPLETED",
            )

        assertEquals("Classic · Rota salva", history.summaryLine())
        assertEquals("Concluída", history.statusLabel())
        assertEquals("10.3 m/s", history.avgSpeedLabel())
    }
}
