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
    version = 4,
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
    }
}
