package com.ghostpin.app.data

import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationSessionModelsTest {

    @Test
    fun `history entity restores full simulation config`() {
        val entity = SimulationHistoryEntity(
            id = "history-1",
            profileIdOrName = "Car",
            routeId = "route-1",
            startLat = -23.0,
            startLng = -46.0,
            endLat = -22.0,
            endLng = -43.0,
            appMode = AppMode.GPX.name,
            waypointsJson = SimulationConfig.serializeWaypoints(
                listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0))
            ),
            waypointPauseSec = 2.5,
            speedRatio = 0.7,
            frequencyHz = 8,
            repeatPolicy = RepeatPolicy.PING_PONG_N.name,
            repeatCount = 3,
            startedAtMs = 1L,
            endedAtMs = 2L,
            durationMs = 1000L,
            avgSpeedMs = 10.0,
            distanceMeters = 100.0,
            resultStatus = "COMPLETED",
        )

        val config = entity.toSimulationConfig()

        assertEquals(AppMode.GPX, config.appMode)
        assertEquals(2, config.waypoints.size)
        assertEquals(2.5, config.waypointPauseSec, 0.0)
        assertEquals(0.7, config.speedRatio, 0.0)
        assertEquals(8, config.frequencyHz)
        assertEquals(RepeatPolicy.PING_PONG_N, config.repeatPolicy)
        assertEquals(3, config.repeatCount)
    }

    @Test
    fun `schedule entity restores full simulation config`() {
        val entity = ScheduleEntity(
            id = "schedule-1",
            startAtMs = 1000L,
            stopAtMs = 2000L,
            profileName = "Pedestrian",
            startLat = -23.0,
            startLng = -46.0,
            endLat = -22.0,
            endLng = -43.0,
            routeId = "route-2",
            appMode = AppMode.WAYPOINTS.name,
            waypointsJson = SimulationConfig.serializeWaypoints(
                listOf(
                    Waypoint(-23.0, -46.0),
                    Waypoint(-22.5, -44.0),
                    Waypoint(-22.0, -43.0),
                )
            ),
            waypointPauseSec = 4.0,
            speedRatio = 0.8,
            frequencyHz = 12,
            repeatPolicy = RepeatPolicy.LOOP_N.name,
            repeatCount = 4,
            enabled = true,
            createdAtMs = 500L,
        )

        val config = entity.toSimulationConfig()

        assertEquals(AppMode.WAYPOINTS, config.appMode)
        assertEquals(3, config.waypoints.size)
        assertEquals(4.0, config.waypointPauseSec, 0.0)
        assertEquals(0.8, config.speedRatio, 0.0)
        assertEquals(12, config.frequencyHz)
        assertEquals(RepeatPolicy.LOOP_N, config.repeatPolicy)
        assertEquals(4, config.repeatCount)
    }
}
