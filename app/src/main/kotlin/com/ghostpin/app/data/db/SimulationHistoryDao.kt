package com.ghostpin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SimulationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SimulationHistoryEntity)

    @Query(
        """
        SELECT * FROM simulation_history
        ORDER BY startedAtMs DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun listPaged(limit: Int, offset: Int): List<SimulationHistoryEntity>

    @Query("SELECT * FROM simulation_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SimulationHistoryEntity?

    @Query(
        """
        UPDATE simulation_history
        SET endedAtMs = :endedAtMs,
            durationMs = :durationMs,
            avgSpeedMs = :avgSpeedMs,
            distanceMeters = :distanceMeters,
            resultStatus = :resultStatus
        WHERE id = :id
        """
    )
    suspend fun closeById(
        id: String,
        endedAtMs: Long,
        durationMs: Long,
        avgSpeedMs: Double,
        distanceMeters: Double,
        resultStatus: String,
    )

    @Query("DELETE FROM simulation_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM simulation_history")
    suspend fun clearHistory()
}
