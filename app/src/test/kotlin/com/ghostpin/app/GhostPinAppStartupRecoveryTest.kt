package com.ghostpin.app

import android.app.AlarmManager
import android.os.Build
import com.ghostpin.app.data.DataStoreSimulationConfigStore
import com.ghostpin.app.data.ProfileManager
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.scheduling.ScheduleDao
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.app.scheduling.ScheduleManager
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class GhostPinAppStartupRecoveryTest {
    @Test
    fun `startup recovery hydrates config and rearms future schedule`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            val context = RuntimeEnvironment.getApplication()
            val configStore = DataStoreSimulationConfigStore(context)
            val config =
                SimulationConfig(
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                    appMode = AppMode.CLASSIC,
                )
            configStore.writeLastUsedConfig(config)

            val scheduleDao = FakeScheduleDao()
            val scheduleManager = ScheduleManager(context, scheduleDao)
            val now = System.currentTimeMillis()
            scheduleDao.upsert(
                ScheduleEntity(
                    id = "startup-schedule",
                    startAtMs = now + 60_000L,
                    stopAtMs = now + 120_000L,
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                    routeId = null,
                    appMode = AppMode.CLASSIC.name,
                    waypointsJson = "[]",
                    waypointPauseSec = 0.0,
                    speedRatio = 1.0,
                    frequencyHz = 5,
                    repeatPolicy = RepeatPolicy.NONE.name,
                    repeatCount = 1,
                    enabled = true,
                    createdAtMs = now,
                )
            )

            val profileDao = FakeProfileDao()
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = profileDao,
                    simulationConfigStore = configStore,
                )
            val profileManager = ProfileManager(profileDao)

            performAppStartupRecovery(
                profileManager = profileManager,
                simulationRepository = repository,
                scheduleManager = scheduleManager,
                schedulingEnabled = true,
            )

            assertEquals(config, repository.lastUsedConfig.value)
            assertTrue(profileDao.insertedProfiles.isNotEmpty())
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)
        }

    @Test
    fun `startup recovery skips rearm when scheduling is disabled`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            val context = RuntimeEnvironment.getApplication()
            val configStore = DataStoreSimulationConfigStore(context)
            configStore.writeLastUsedConfig(
                SimulationConfig(
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                )
            )

            val scheduleDao = FakeScheduleDao()
            val scheduleManager = ScheduleManager(context, scheduleDao)
            val now = System.currentTimeMillis()
            scheduleDao.upsert(
                ScheduleEntity(
                    id = "disabled-schedule",
                    startAtMs = now + 60_000L,
                    stopAtMs = now + 120_000L,
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                    routeId = null,
                    appMode = AppMode.CLASSIC.name,
                    waypointsJson = "[]",
                    waypointPauseSec = 0.0,
                    speedRatio = 1.0,
                    frequencyHz = 5,
                    repeatPolicy = RepeatPolicy.NONE.name,
                    repeatCount = 1,
                    enabled = true,
                    createdAtMs = now,
                )
            )

            val profileDao = FakeProfileDao()
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = profileDao,
                    simulationConfigStore = configStore,
                )
            val profileManager = ProfileManager(profileDao)

            performAppStartupRecovery(
                profileManager = profileManager,
                simulationRepository = repository,
                scheduleManager = scheduleManager,
                schedulingEnabled = false,
            )

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
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
        val insertedProfiles = mutableListOf<ProfileEntity>()

        override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(insertedProfiles.toList())

        override suspend fun getAll(): List<ProfileEntity> = insertedProfiles.toList()

        override suspend fun getById(id: String): ProfileEntity? = insertedProfiles.firstOrNull { it.id == id }

        override suspend fun getByName(name: String): ProfileEntity? = insertedProfiles.firstOrNull { it.name == name }

        override suspend fun countCustom(): Int = insertedProfiles.count { !it.isBuiltIn }

        override suspend fun insert(profile: ProfileEntity) {
            insertedProfiles += profile
        }

        override suspend fun insertAll(profiles: List<ProfileEntity>) {
            insertedProfiles += profiles
        }

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

        override suspend fun interruptRunningSessions(
            endedAtMs: Long,
            excludeId: String?
        ) = Unit
    }

    private class FakeScheduleDao : ScheduleDao {
        private val schedules = linkedMapOf<String, ScheduleEntity>()

        override fun observeEnabledSchedules(): Flow<List<ScheduleEntity>> = emptyFlow()

        override suspend fun getById(scheduleId: String): ScheduleEntity? = schedules[scheduleId]

        override suspend fun getAllEnabled(): List<ScheduleEntity> = schedules.values.filter { it.enabled }

        override suspend fun findEnabledByStartAt(startAtMs: Long): List<ScheduleEntity> =
            schedules.values.filter {
                it.enabled &&
                    it.startAtMs == startAtMs
            }

        override suspend fun upsert(schedule: ScheduleEntity) {
            schedules[schedule.id] = schedule
        }

        override suspend fun disable(scheduleId: String) {
            val current = schedules[scheduleId] ?: return
            schedules[scheduleId] = current.copy(enabled = false)
        }
    }
}
