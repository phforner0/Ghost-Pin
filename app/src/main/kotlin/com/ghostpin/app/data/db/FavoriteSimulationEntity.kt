package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_simulations")
data class FavoriteSimulationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileIdOrName: String,
    val routeId: String?,
    val speedRatio: Double,
    val frequencyHz: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
