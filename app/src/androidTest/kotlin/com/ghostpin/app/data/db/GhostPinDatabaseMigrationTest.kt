package com.ghostpin.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GhostPinDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "schemas",
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate4To5_preservesRowsAndAddsSessionColumns() {
        val dbName = "migration-test"

        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO favorite_simulations
                (id, name, profileIdOrName, routeId, speedRatio, frequencyHz, createdAtMs, updatedAtMs)
                VALUES ('fav-1', 'Fav', 'Car', 'route-1', 0.8, 10, 1, 2)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO simulation_history
                (id, profileIdOrName, routeId, startedAtMs, endedAtMs, durationMs, avgSpeedMs, distanceMeters, resultStatus)
                VALUES ('hist-1', 'Car', 'route-1', 10, 20, 10, 5.0, 100.0, 'COMPLETED')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO schedules
                (id, startAtMs, stopAtMs, profileName, startLat, startLng, speedRatio, frequencyHz, enabled, createdAtMs)
                VALUES ('sch-1', 100, 200, 'Car', -23.0, -46.0, 0.7, 8, 1, 50)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            GhostPinDatabase.MIGRATION_4_5,
        )

        migratedDb.query("SELECT startLat, endLat, appMode, waypointsJson, repeatPolicy, repeatCount FROM favorite_simulations WHERE id = 'fav-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(-23.5505, cursor.getDouble(0), 0.0)
                assertEquals(-22.9068, cursor.getDouble(1), 0.0)
                assertEquals("CLASSIC", cursor.getString(2))
                assertEquals("[]", cursor.getString(3))
                assertEquals("NONE", cursor.getString(4))
                assertEquals(1, cursor.getInt(5))
            }

        migratedDb.query("SELECT speedRatio, frequencyHz, appMode, waypointsJson FROM simulation_history WHERE id = 'hist-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(1.0, cursor.getDouble(0), 0.0)
                assertEquals(5, cursor.getInt(1))
                assertEquals("CLASSIC", cursor.getString(2))
                assertEquals("[]", cursor.getString(3))
            }

        migratedDb.query("SELECT routeId, appMode, repeatPolicy, repeatCount FROM schedules WHERE id = 'sch-1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(null, cursor.getString(0))
                assertEquals("CLASSIC", cursor.getString(1))
                assertEquals("NONE", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
    }
}
