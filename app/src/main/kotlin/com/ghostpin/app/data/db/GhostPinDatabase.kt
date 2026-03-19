package com.ghostpin.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * GhostPin Room database.
 *
 * Sprint 4 — Tasks 14 & 17.
 *
 * Version history:
 *   1 — Sprint 4: profiles + routes tables.
 *   2 — Sprint 7: simulation_history table.
 *
 * Migrations: use [androidx.room.migration.Migration] for incremental schema changes
 * in future sprints. destructiveMigration is NOT enabled — data must be preserved.
 */
@Database(
    entities = [ProfileEntity::class, RouteEntity::class, SimulationHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class GhostPinDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun routeDao(): RouteDao
    abstract fun simulationHistoryDao(): SimulationHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simulation_history` (
                        `id` TEXT NOT NULL,
                        `profileIdOrName` TEXT NOT NULL,
                        `routeId` TEXT,
                        `startedAtMs` INTEGER NOT NULL,
                        `endedAtMs` INTEGER,
                        `durationMs` INTEGER,
                        `avgSpeedMs` REAL,
                        `distanceMeters` REAL NOT NULL,
                        `resultStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
