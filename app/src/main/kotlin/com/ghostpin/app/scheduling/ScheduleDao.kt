package com.ghostpin.app.scheduling

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE enabled = 1 ORDER BY startAtMs ASC")
    fun observeEnabledSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :scheduleId LIMIT 1")
    suspend fun getById(scheduleId: String): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun getAllEnabled(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE enabled = 1 AND startAtMs = :startAtMs")
    suspend fun findEnabledByStartAt(startAtMs: Long): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET enabled = 0 WHERE id = :scheduleId")
    suspend fun disable(scheduleId: String)
}
