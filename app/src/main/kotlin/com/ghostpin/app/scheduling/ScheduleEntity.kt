package com.ghostpin.app.scheduling

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val startAtMs: Long,
    val stopAtMs: Long?,
    val profileName: String,
    val startLat: Double,
    val startLng: Double,
    val speedRatio: Double,
    val frequencyHz: Int,
    val enabled: Boolean,
    val createdAtMs: Long,
)
