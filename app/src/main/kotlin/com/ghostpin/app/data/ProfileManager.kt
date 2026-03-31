package com.ghostpin.app.data

import android.util.Log
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.security.LogSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProfileManager — single source of truth for movement profiles.
 *
 * Sprint 4 — Tasks 14 & 15.
 *
 * Responsibilities:
 *  - Seeds the 5 built-in profiles on first launch (idempotent).
 *  - Exposes a reactive [Flow] of all profiles for the UI.
 *  - Provides CRUD for custom profiles with semver versioning.
 *  - Enforces that built-in profiles cannot be deleted.
 *
 * Semver convention:
 *  - Built-in profiles start at "1.0.0".
 *  - Clone of a built-in starts at "1.0.0" (independent copy).
 *  - Each [update] call bumps the patch version automatically (1.0.0 → 1.0.1).
 *  - Callers may pass an explicit version string for major/minor bumps.
 *
 * @param dao Injected Room DAO — all DB access is delegated here.
 */
@Singleton
class ProfileManager
    @Inject
    constructor(
        private val dao: ProfileDao,
    ) {
        companion object {
            private const val TAG = "ProfileManager"

            // Stable IDs for the 5 built-in profiles — must never change after ship.
            private const val ID_PEDESTRIAN = "builtin_pedestrian"
            private const val ID_BICYCLE = "builtin_bicycle"
            private const val ID_CAR = "builtin_car"
            private const val ID_URBAN_VEHICLE = "builtin_urban_vehicle"
            private const val ID_DRONE = "builtin_drone"

            /** The 5 built-in profiles with their stable IDs. */
            private val BUILT_IN_PROFILES: List<Pair<String, MovementProfile>> =
                listOf(
                    ID_PEDESTRIAN to MovementProfile.PEDESTRIAN,
                    ID_BICYCLE to MovementProfile.BICYCLE,
                    ID_CAR to MovementProfile.CAR,
                    ID_URBAN_VEHICLE to MovementProfile.URBAN_VEHICLE,
                    ID_DRONE to MovementProfile.DRONE,
                )
        }

        // ── Seeding ─────────────────────────────────────────────────────────────

        /**
         * Seed built-in profiles if they don't exist yet.
         *
         * Safe to call on every app launch — [OnConflictStrategy.IGNORE] means
         * existing built-ins are never overwritten.
         *
         * Call from [com.ghostpin.app.GhostPinApp.onCreate] or a Hilt initializer.
         */
        suspend fun seedBuiltInsIfNeeded() {
            val entities =
                BUILT_IN_PROFILES.map { (id, profile) ->
                    ProfileEntity.fromDomain(
                        profile = profile,
                        id = id,
                        isBuiltIn = true,
                        isCustom = false,
                        version = "1.0.0",
                    )
                }
            dao.insertAll(entities)
            Log.d(TAG, "Built-in profiles seeded (no-op if already present).")
        }

        // ── Reads ────────────────────────────────────────────────────────────────

        /**
         * Observe all profiles as domain models.
         *
         * Built-ins appear first (sorted by [ProfileEntity.isBuiltIn] DESC),
         * then custom profiles alphabetically.
         */
        fun observeAll(): Flow<List<MovementProfile>> =
            dao.observeAll().map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Observe all profiles with their full entity metadata (for the management UI).
         */
        fun observeAllEntities(): Flow<List<ProfileEntity>> = dao.observeAll()

        /** Get a profile by Room entity ID. Returns null if not found. */
        suspend fun getById(id: String): MovementProfile? = dao.getById(id)?.toDomain()

        /** Get a profile entity by ID (includes metadata like version and isBuiltIn). */
        suspend fun getEntityById(id: String): ProfileEntity? = dao.getById(id)

        // ── Writes ────────────────────────────────────────────────────────────────

        /**
         * Create a new custom profile.
         *
         * @param profile Domain model to persist.
         * @param name Display name (may differ from [profile.name] for clones).
         * @return The generated UUID for the new profile.
         */
        suspend fun create(
            profile: MovementProfile,
            name: String = profile.name
        ): String {
            val id = UUID.randomUUID().toString()
            val entity =
                ProfileEntity.fromDomain(
                    profile = profile.copy(name = name),
                    id = id,
                    isBuiltIn = false,
                    isCustom = true,
                    version = "1.0.0",
                )
            dao.insert(entity)
            Log.d(TAG, LogSanitizer.sanitizeString("Profile created: id=$id name=$name"))
            return id
        }

        /**
         * Update an existing custom profile.
         *
         * Bumps the patch version automatically (1.0.0 → 1.0.1).
         * Throws [IllegalStateException] if the profile is a built-in.
         *
         * @param id Room entity ID of the profile to update.
         * @param updated The new domain model values.
         */
        suspend fun update(
            id: String,
            updated: MovementProfile
        ) {
            val existing =
                dao.getById(id)
                    ?: error("Profile not found: $id")
            check(!existing.isBuiltIn) {
                "Cannot modify built-in profile '${existing.name}' (id=$id)"
            }

            val newVersion = bumpPatch(existing.version)
            val entity =
                ProfileEntity
                    .fromDomain(
                        profile = updated,
                        id = id,
                        isBuiltIn = false,
                        isCustom = true,
                        version = newVersion,
                    ).copy(
                        createdAtMs = existing.createdAtMs, // preserve original creation time
                        updatedAtMs = System.currentTimeMillis(),
                    )
            dao.update(entity)
            Log.d(
                TAG,
                LogSanitizer.sanitizeString("Profile updated: id=$id version ${existing.version} -> $newVersion")
            )
        }

        /**
         * Clone a profile (built-in or custom) under a new name.
         *
         * The clone starts at version "1.0.0" as an independent custom profile.
         *
         * @param sourceId Room entity ID of the profile to clone.
         * @param newName Display name for the clone.
         * @return UUID of the newly created clone, or null if source not found.
         */
        suspend fun clone(
            sourceId: String,
            newName: String
        ): String? {
            val source =
                dao.getById(sourceId) ?: run {
                    Log.w(TAG, LogSanitizer.sanitizeString("Clone failed: source not found (id=$sourceId)"))
                    return null
                }
            val cloneId = UUID.randomUUID().toString()
            val clone =
                source.copy(
                    id = cloneId,
                    name = newName,
                    isBuiltIn = false,
                    isCustom = true,
                    version = "1.0.0",
                    createdAtMs = System.currentTimeMillis(),
                    updatedAtMs = System.currentTimeMillis(),
                )
            dao.insert(clone)
            Log.d(TAG, LogSanitizer.sanitizeString("Profile cloned: source=$sourceId -> clone=$cloneId name=$newName"))
            return cloneId
        }

        /**
         * Delete a custom profile by ID.
         *
         * Throws [IllegalStateException] if the profile is built-in.
         * No-op (logs warning) if the profile doesn't exist.
         */
        suspend fun delete(id: String) {
            val existing =
                dao.getById(id) ?: run {
                    Log.w(TAG, LogSanitizer.sanitizeString("Delete no-op: profile not found (id=$id)"))
                    return
                }
            check(!existing.isBuiltIn) {
                "Cannot delete built-in profile '${existing.name}' (id=$id)"
            }
            dao.deleteById(id)
            Log.d(TAG, LogSanitizer.sanitizeString("Profile deleted: id=$id name=${existing.name}"))
        }

        // ── Semver helpers ────────────────────────────────────────────────────────

        /**
         * Bump the patch component of a semver string.
         *
         * "1.0.0" → "1.0.1", "2.3.9" → "2.3.10".
         * Falls back to "1.0.1" if the string is not a valid semver.
         */
        private fun bumpPatch(version: String): String {
            val parts = version.split(".").mapNotNull { it.toIntOrNull() }
            return if (parts.size == 3) {
                "${parts[0]}.${parts[1]}.${parts[2] + 1}"
            } else {
                Log.w(TAG, LogSanitizer.sanitizeString("Invalid semver '$version' — resetting to 1.0.1"))
                "1.0.1"
            }
        }
    }
