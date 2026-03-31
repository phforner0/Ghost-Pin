package com.ghostpin.app.scheduling

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val startAtMs: Long,
    val stopAtMs: Long?,
    val profileName: String,
    val profileLookupKey: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val routeId: String?,
    val appMode: String,
    val waypointsJson: String,
    val waypointPauseSec: Double,
    val speedRatio: Double,
    val frequencyHz: Int,
    val repeatPolicy: String,
    val repeatCount: Int,
    val enabled: Boolean,
    val createdAtMs: Long
) {
    fun toSimulationConfig(): SimulationConfig =
        SimulationConfig(
            profileName = profileName,
            profileLookupKey = profileLookupKey,
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
