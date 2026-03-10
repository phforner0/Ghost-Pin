package com.ghostpin.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [RouteEntity] CRUD operations.
 *
 * Sprint 4 — Task 17: Route editor with persistence.
 */
@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity)

    @Update
    suspend fun update(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun count(): Int
}
