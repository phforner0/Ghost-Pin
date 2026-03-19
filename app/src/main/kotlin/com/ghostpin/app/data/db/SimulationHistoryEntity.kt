package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for simulation execution history.
 */
@Entity(tableName = "simulation_history")
data class SimulationHistoryEntity(
    @PrimaryKey val id: String,
    val profileIdOrName: String,
    val routeId: String?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val durationMs: Long?,
    val avgSpeedMs: Double?,
    val distanceMeters: Double,
    val resultStatus: String,
)
