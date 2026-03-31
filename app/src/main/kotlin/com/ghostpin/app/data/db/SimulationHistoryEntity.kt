package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy

/**
 * Room entity for simulation execution history.
 */
@Entity(tableName = "simulation_history")
data class SimulationHistoryEntity(
    @PrimaryKey val id: String,
    val profileIdOrName: String,
    val routeId: String?,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val appMode: String,
    val waypointsJson: String,
    val waypointPauseSec: Double,
    val speedRatio: Double,
    val frequencyHz: Int,
    val repeatPolicy: String,
    val repeatCount: Int,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val durationMs: Long?,
    val avgSpeedMs: Double?,
    val distanceMeters: Double,
    val resultStatus: String
) {
    fun toSimulationConfig(): SimulationConfig = SimulationConfig(
        profileName = profileIdOrName,
        startLat = startLat,
        startLng = startLng,
        endLat = endLat,
        endLng = endLng,
        routeId = routeId,
        appMode = runCatching { AppMode.valueOf(appMode) }.getOrDefault(AppMode.CLASSIC),
        waypoints = SimulationConfig.deserializeWaypoints(waypointsJson),
        waypointPauseSec = waypointPauseSec,
        speedRatio = speedRatio,
        frequencyHz = frequencyHz,
        repeatPolicy = runCatching { RepeatPolicy.valueOf(repeatPolicy) }.getOrDefault(RepeatPolicy.NONE),
        repeatCount = repeatCount,
    )
}
