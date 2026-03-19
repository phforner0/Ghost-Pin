package com.ghostpin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteSimulationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteSimulationEntity)

    @Update
    suspend fun update(entity: FavoriteSimulationEntity)

    @Query("SELECT * FROM favorite_simulations ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<FavoriteSimulationEntity>>

    @Query("SELECT * FROM favorite_simulations ORDER BY updatedAtMs DESC")
    suspend fun listRecent(): List<FavoriteSimulationEntity>

    @Query("SELECT * FROM favorite_simulations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FavoriteSimulationEntity?

    @Query("SELECT * FROM favorite_simulations ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun getMostRecent(): FavoriteSimulationEntity?

    @Query("DELETE FROM favorite_simulations WHERE id = :id")
    suspend fun deleteById(id: String)
}
