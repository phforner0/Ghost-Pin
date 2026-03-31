package com.ghostpin.app.scheduling

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.service.SimulationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ScheduleReceiverInstrumentedTest {
    @Test
    fun onReceive_startEventForwardsFullScheduleConfigToService() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val startedLatch = CountDownLatch(1)
        val capturingContext = CapturingContext(targetContext, startedLatch)
        val schedule =
            ScheduleEntity(
                id = "schedule-1",
                startAtMs = System.currentTimeMillis() + 60_000L,
                stopAtMs = null,
                profileName = "Car",
                profileLookupKey = "builtin_car",
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.0,
                endLng = -43.0,
                routeId = "route-1",
                appMode = com.ghostpin.core.model.AppMode.WAYPOINTS.name,
                waypointsJson =
                    com.ghostpin.app.data.SimulationConfig.serializeWaypoints(
                        listOf(
                            com.ghostpin.core.model
                                .Waypoint(-23.0, -46.0),
                            com.ghostpin.core.model
                                .Waypoint(-22.5, -44.0),
                            com.ghostpin.core.model
                                .Waypoint(-22.0, -43.0),
                        )
                    ),
                waypointPauseSec = 4.0,
                speedRatio = 0.75,
                frequencyHz = 12,
                repeatPolicy = com.ghostpin.engine.interpolation.RepeatPolicy.LOOP_N.name,
                repeatCount = 3,
                enabled = true,
                createdAtMs = 1L,
            )

        val receiver =
            ScheduleReceiver().apply {
                scheduleDao = FakeScheduleDao(schedule)
                scheduleManager = ScheduleManager(capturingContext, FakeScheduleDao(schedule))
                simulationRepository =
                    SimulationRepository(
                        simulationHistoryDao = FakeSimulationHistoryDao(),
                        favoriteSimulationDao = FakeFavoriteSimulationDao(),
                        routeDao = FakeRouteDao(),
                        profileDao = FakeProfileDao(),
                    )
            }

        receiver.onReceive(
            capturingContext,
            Intent(ScheduleReceiver.ACTION_SCHEDULE_EVENT).apply {
                putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, schedule.id)
                putExtra(ScheduleReceiver.EXTRA_EVENT, ScheduleReceiver.EVENT_START)
            }
        )

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        val startedIntent = capturingContext.startedForegroundIntent
        assertNotNull(startedIntent)
        startedIntent!!
        assertEquals(SimulationService.ACTION_START, startedIntent.action)
        assertEquals(schedule.profileName, startedIntent.getStringExtra(SimulationService.EXTRA_PROFILE_NAME))
        assertEquals(schedule.startLat, startedIntent.getDoubleExtra(SimulationService.EXTRA_START_LAT, 0.0), 0.0)
        assertEquals(schedule.startLng, startedIntent.getDoubleExtra(SimulationService.EXTRA_START_LNG, 0.0), 0.0)
        assertEquals(schedule.endLat, startedIntent.getDoubleExtra(SimulationService.EXTRA_END_LAT, 0.0), 0.0)
        assertEquals(schedule.endLng, startedIntent.getDoubleExtra(SimulationService.EXTRA_END_LNG, 0.0), 0.0)
        assertEquals(schedule.routeId, startedIntent.getStringExtra(SimulationService.EXTRA_ROUTE_ID))
        assertEquals(schedule.appMode, startedIntent.getStringExtra(SimulationService.EXTRA_MODE))
        assertEquals(
            schedule.waypointPauseSec,
            startedIntent.getDoubleExtra(SimulationService.EXTRA_WAYPOINT_PAUSE_SEC, 0.0),
            0.0
        )
        assertEquals(schedule.speedRatio, startedIntent.getDoubleExtra(SimulationService.EXTRA_SPEED_RATIO, 0.0), 0.0)
        assertEquals(schedule.frequencyHz, startedIntent.getIntExtra(SimulationService.EXTRA_FREQUENCY_HZ, 0))
        assertEquals(schedule.repeatPolicy, startedIntent.getStringExtra(SimulationService.EXTRA_REPEAT_POLICY))
        assertEquals(schedule.repeatCount, startedIntent.getIntExtra(SimulationService.EXTRA_REPEAT_COUNT, 0))
        assertArrayEquals(
            doubleArrayOf(-23.0, -22.5, -22.0),
            startedIntent.getDoubleArrayExtra(SimulationService.EXTRA_WAYPOINTS_LAT),
            0.0
        )
        assertArrayEquals(
            doubleArrayOf(-46.0, -44.0, -43.0),
            startedIntent.getDoubleArrayExtra(SimulationService.EXTRA_WAYPOINTS_LNG),
            0.0
        )
    }

    private class CapturingContext(
        base: Context,
        private val latch: CountDownLatch,
    ) : ContextWrapper(base) {
        @Volatile var startedForegroundIntent: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName? {
            startedForegroundIntent = service
            latch.countDown()
            return null
        }
    }

    private class FakeScheduleDao(
        private val schedule: ScheduleEntity,
    ) : ScheduleDao {
        override fun observeEnabledSchedules(): Flow<List<ScheduleEntity>> = flowOf(listOf(schedule))

        override suspend fun getById(scheduleId: String): ScheduleEntity? =
            if (schedule.id ==
                scheduleId
            ) {
                schedule
            } else {
                null
            }

        override suspend fun getAllEnabled(): List<ScheduleEntity> =
            if (schedule.enabled) listOf(schedule) else emptyList()

        override suspend fun findEnabledByStartAt(startAtMs: Long): List<ScheduleEntity> =
            if (schedule.enabled && schedule.startAtMs == startAtMs) listOf(schedule) else emptyList()

        override suspend fun upsert(schedule: ScheduleEntity) = Unit

        override suspend fun disable(scheduleId: String) = Unit
    }

    private class FakeFavoriteSimulationDao : FavoriteSimulationDao {
        override suspend fun upsert(entity: FavoriteSimulationEntity) = Unit

        override suspend fun update(entity: FavoriteSimulationEntity) = Unit

        override fun observeAll(): Flow<List<FavoriteSimulationEntity>> = emptyFlow()

        override suspend fun listRecent(): List<FavoriteSimulationEntity> = emptyList()

        override suspend fun getById(id: String): FavoriteSimulationEntity? = null

        override suspend fun getMostRecent(): FavoriteSimulationEntity? = null

        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeRouteDao : RouteDao {
        override fun observeAll(): Flow<List<RouteEntity>> = emptyFlow()

        override suspend fun getById(id: String): RouteEntity? = null

        override suspend fun insert(route: RouteEntity) = Unit

        override suspend fun update(route: RouteEntity) = Unit

        override suspend fun deleteById(id: String) = Unit

        override suspend fun count(): Int = 0
    }

    private class FakeProfileDao : ProfileDao {
        override fun observeAll(): Flow<List<ProfileEntity>> = emptyFlow()

        override suspend fun getAll(): List<ProfileEntity> = emptyList()

        override suspend fun getById(id: String): ProfileEntity? = null

        override suspend fun getByName(name: String): ProfileEntity? = null

        override suspend fun countCustom(): Int = 0

        override suspend fun insert(profile: ProfileEntity) = Unit

        override suspend fun insertAll(profiles: List<ProfileEntity>) = Unit

        override suspend fun update(profile: ProfileEntity) = Unit

        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeSimulationHistoryDao : SimulationHistoryDao {
        override suspend fun insert(entity: SimulationHistoryEntity) = Unit

        override suspend fun listPaged(
            limit: Int,
            offset: Int
        ): List<SimulationHistoryEntity> = emptyList()

        override suspend fun getById(id: String): SimulationHistoryEntity? = null

        override suspend fun closeById(
            id: String,
            endedAtMs: Long,
            durationMs: Long,
            avgSpeedMs: Double,
            distanceMeters: Double,
            resultStatus: String,
        ) = Unit

        override suspend fun deleteById(id: String) = Unit

        override suspend fun clearHistory() = Unit
    }
}
