package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy

@Entity(tableName = "favorite_simulations")
data class FavoriteSimulationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileName: String,
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
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toSimulationConfig(): SimulationConfig =
        SimulationConfig(
            profileName = profileName,
            profileLookupKey = profileIdOrName,
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
