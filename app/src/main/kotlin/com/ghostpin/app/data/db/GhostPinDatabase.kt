package com.ghostpin.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * GhostPin Room database.
 *
 * Sprint 4 — Tasks 14 & 17.
 *
 * Version history:
 *   1 — Sprint 4: profiles + routes tables.
 *
 * Migrations: use [androidx.room.migration.Migration] for incremental schema changes
 * in future sprints. destructiveMigration is NOT enabled — data must be preserved.
 */
@Database(
    entities = [ProfileEntity::class, RouteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GhostPinDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun routeDao(): RouteDao
}
