package com.ghostpin.app.ui

import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulePresentationTest {
    @Test
    fun `routeSummary prefers saved route ids`() {
        val config =
            SimulationConfig(
                profileName = "Car",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                routeId = "route-1",
                appMode = AppMode.GPX,
                waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
            )

        assertEquals("Rota salva", config.routeSummary())
    }

    @Test
    fun `routeSummary distinguishes imported and joystick sessions`() {
        assertEquals("Rota importada (3 pts)", scheduleRouteSummary(null, AppMode.GPX, 3))
        assertEquals("Sem rota fixa", scheduleRouteSummary(null, AppMode.JOYSTICK, 0))
    }

    @Test
    fun `formatRelativeScheduleTime formats future and past windows`() {
        val nowMs = 1_000_000L

        assertEquals("em 2h 30min", formatRelativeScheduleTime(nowMs + 9_000_000L, nowMs))
        assertEquals("há 15min", formatRelativeScheduleTime(nowMs - 900_000L, nowMs))
        assertEquals("agora", formatRelativeScheduleTime(nowMs + 10_000L, nowMs))
    }

    @Test
    fun `windowSummary describes active schedule windows`() {
        val nowMs = 1_000_000L
        val schedule =
            ScheduleEntity(
                id = "schedule-1",
                startAtMs = nowMs - 60_000L,
                stopAtMs = nowMs + 300_000L,
                profileName = "Car",
                profileLookupKey = "builtin_car",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                routeId = null,
                appMode = AppMode.WAYPOINTS.name,
                waypointsJson =
                    SimulationConfig.serializeWaypoints(
                        listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0))
                    ),
                waypointPauseSec = 0.0,
                speedRatio = 1.0,
                frequencyHz = 5,
                repeatPolicy = RepeatPolicy.NONE.name,
                repeatCount = 1,
                enabled = true,
                createdAtMs = nowMs,
            )

        assertEquals("Em andamento · termina em 5min", schedule.windowSummary(nowMs))
        assertEquals("2 waypoints", schedule.routeSummary())
        assertEquals("Waypoints", schedule.modeDisplayName())
    }
}
