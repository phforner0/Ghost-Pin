package com.ghostpin.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ghostpin.app.scheduling.ScheduleDao
import com.ghostpin.app.scheduling.ScheduleEntity

/**
 * GhostPin Room database.
 */
@Database(
    entities = [
        ProfileEntity::class,
        RouteEntity::class,
        SimulationHistoryEntity::class,
        FavoriteSimulationEntity::class,
        ScheduleEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class GhostPinDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun routeDao(): RouteDao
    abstract fun simulationHistoryDao(): SimulationHistoryDao
    abstract fun favoriteSimulationDao(): FavoriteSimulationDao
    abstract fun scheduleDao(): ScheduleDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_simulations` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `profileIdOrName` TEXT NOT NULL,
                        `routeId` TEXT,
                        `speedRatio` REAL NOT NULL,
                        `frequencyHz` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedules` (
                        `id` TEXT NOT NULL,
                        `startAtMs` INTEGER NOT NULL,
                        `stopAtMs` INTEGER,
                        `profileName` TEXT NOT NULL,
                        `startLat` REAL NOT NULL,
                        `startLng` REAL NOT NULL,
                        `speedRatio` REAL NOT NULL,
                        `frequencyHz` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `startLat` REAL NOT NULL DEFAULT -23.5505")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `startLng` REAL NOT NULL DEFAULT -46.6333")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `endLat` REAL NOT NULL DEFAULT -22.9068")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `endLng` REAL NOT NULL DEFAULT -43.1729")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `appMode` TEXT NOT NULL DEFAULT 'CLASSIC'")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `waypointsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `waypointPauseSec` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `repeatPolicy` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `favorite_simulations` ADD COLUMN `repeatCount` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `startLat` REAL NOT NULL DEFAULT -23.5505")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `startLng` REAL NOT NULL DEFAULT -46.6333")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `endLat` REAL NOT NULL DEFAULT -22.9068")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `endLng` REAL NOT NULL DEFAULT -43.1729")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `appMode` TEXT NOT NULL DEFAULT 'CLASSIC'")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `waypointsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `waypointPauseSec` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `speedRatio` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `frequencyHz` INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `repeatPolicy` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `simulation_history` ADD COLUMN `repeatCount` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `endLat` REAL NOT NULL DEFAULT -22.9068")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `endLng` REAL NOT NULL DEFAULT -43.1729")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `routeId` TEXT")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `appMode` TEXT NOT NULL DEFAULT 'CLASSIC'")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `waypointsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `waypointPauseSec` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `repeatPolicy` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `repeatCount` INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
