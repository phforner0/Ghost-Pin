package com.ghostpin.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [ProfileEntity] CRUD operations.
 *
 * Sprint 4 — Task 14: ProfileManager with Room.
 *
 * All operations that return [Flow] are observed live — the UI reacts
 * automatically when profiles are created, updated, or deleted.
 */
@Dao
interface ProfileDao {

    // ── Reads ──────────────────────────────────────────────────────────────

    /** Observe all profiles ordered by built-in first, then alphabetically. */
    @Query("SELECT * FROM profiles ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    /** One-shot read of all profiles (for migration / seeding checks). */
    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<ProfileEntity>

    /** Find a profile by its unique ID. Returns null if not found. */
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    /** Find a profile by name (used for deduplication during seeding). */
    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProfileEntity?

    /** Count all user-created (non-built-in) profiles. */
    @Query("SELECT COUNT(*) FROM profiles WHERE isBuiltIn = 0")
    suspend fun countCustom(): Int

    // ── Writes ─────────────────────────────────────────────────────────────

    /** Insert a new profile. Replaces on conflict (used for seeding built-ins). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    /** Insert multiple profiles in one transaction. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(profiles: List<ProfileEntity>)

    /** Update a profile (full replace by primary key). */
    @Update
    suspend fun update(profile: ProfileEntity)

    /**
     * Delete a profile by ID.
     *
     * Callers must verify [ProfileEntity.isBuiltIn] == false before calling;
     * the DAO itself does not enforce this to keep it generic.
     */
    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
