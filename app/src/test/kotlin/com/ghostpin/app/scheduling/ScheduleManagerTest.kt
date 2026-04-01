package com.ghostpin.app.scheduling

import android.app.AlarmManager
import android.os.Build
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ScheduleManagerTest {
    @Test
    fun `uses exact alarms below android s`() {
        assertTrue(shouldUseExactAlarms(Build.VERSION_CODES.R, canScheduleExactAlarms = false))
    }

    @Test
    fun `uses exact alarms on android s when permission is granted`() {
        assertTrue(shouldUseExactAlarms(Build.VERSION_CODES.S, canScheduleExactAlarms = true))
    }

    @Test
    fun `falls back to inexact alarms on android s when exact alarm access is unavailable`() {
        assertFalse(shouldUseExactAlarms(Build.VERSION_CODES.S, canScheduleExactAlarms = false))
    }

    @Test
    fun `createSchedule stores schedule and arms start plus stop alarms`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            val context = RuntimeEnvironment.getApplication()
            val dao = FakeScheduleDao()
            val manager = ScheduleManager(context, dao)
            val now = System.currentTimeMillis()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            clearScheduledAlarms(alarmManager)

            val result =
                manager.createSchedule(
                    ScheduleManager.CreateScheduleRequest(
                        startAtMs = now + 60_000L,
                        stopAtMs = now + 120_000L,
                        config = sampleConfig(),
                    )
                )

            assertTrue(result is ScheduleManager.CreateScheduleResult.Success)
            val success = result as ScheduleManager.CreateScheduleResult.Success
            assertTrue(success.exactAlarmGranted)
            assertEquals(1, dao.schedules.size)

            val scheduled = shadowOf(alarmManager).scheduledAlarms
            assertEquals(2, scheduled.size)
            assertTrue(scheduled.all { it.isAllowWhileIdle })
        }

    @Test
    fun `createSchedule returns conflict when start time already exists`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val dao = FakeScheduleDao()
            val manager = ScheduleManager(context, dao)
            val now = System.currentTimeMillis()
            clearScheduledAlarms(context.getSystemService(AlarmManager::class.java))
            val request =
                ScheduleManager.CreateScheduleRequest(
                    startAtMs = now + 60_000L,
                    stopAtMs = null,
                    config = sampleConfig(),
                )

            manager.createSchedule(request)
            val second = manager.createSchedule(request)

            assertTrue(second is ScheduleManager.CreateScheduleResult.Conflict)
            assertEquals(1, dao.schedules.size)
        }

    @Test
    fun `cancelSchedule disables schedule and clears its alarms`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            val context = RuntimeEnvironment.getApplication()
            val dao = FakeScheduleDao()
            val manager = ScheduleManager(context, dao)
            val now = System.currentTimeMillis()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            clearScheduledAlarms(alarmManager)

            val created =
                manager.createSchedule(
                    ScheduleManager.CreateScheduleRequest(
                        startAtMs = now + 60_000L,
                        stopAtMs = now + 120_000L,
                        config = sampleConfig(),
                    )
                ) as ScheduleManager.CreateScheduleResult.Success

            manager.cancelSchedule(created.schedule.id)

            val stored = dao.getById(created.schedule.id)!!
            assertFalse(stored.enabled)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        }

    @Test
    fun `rearmPersistedSchedules disables expired schedules and rearms active ones`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            val context = RuntimeEnvironment.getApplication()
            val dao = FakeScheduleDao()
            val manager = ScheduleManager(context, dao)
            val now = System.currentTimeMillis()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            clearScheduledAlarms(alarmManager)

            val expired = sampleSchedule(id = "expired", startAtMs = now - 120_000L, stopAtMs = now - 60_000L)
            val active = sampleSchedule(id = "active", startAtMs = now + 60_000L, stopAtMs = now + 120_000L)
            dao.upsert(expired)
            dao.upsert(active)

            manager.rearmPersistedSchedules(now)

            assertFalse(dao.getById("expired")!!.enabled)
            assertTrue(dao.getById("active")!!.enabled)
            val scheduled = shadowOf(alarmManager).scheduledAlarms
            assertEquals(2, scheduled.size)
        }

    @Test
    fun `rearmPersistedSchedules disables schedules whose start was missed`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            val context = RuntimeEnvironment.getApplication()
            val dao = FakeScheduleDao()
            val manager = ScheduleManager(context, dao)
            val now = System.currentTimeMillis()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            clearScheduledAlarms(alarmManager)

            val missed = sampleSchedule(id = "missed", startAtMs = now - 60_000L, stopAtMs = now + 60_000L)
            dao.upsert(missed)

            manager.rearmPersistedSchedules(now)

            assertFalse(dao.getById("missed")!!.enabled)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        }

    private fun sampleConfig() =
        SimulationConfig(
            profileName = "Car",
            profileLookupKey = "builtin_car",
            startLat = -23.0,
            startLng = -46.0,
            endLat = -22.0,
            endLng = -43.0,
            routeId = null,
            appMode = AppMode.CLASSIC,
            waypointPauseSec = 0.0,
            speedRatio = 1.0,
            frequencyHz = 5,
            repeatPolicy = RepeatPolicy.NONE,
            repeatCount = 1,
        )

    private fun sampleSchedule(
        id: String,
        startAtMs: Long,
        stopAtMs: Long?
    ) = ScheduleEntity(
        id = id,
        startAtMs = startAtMs,
        stopAtMs = stopAtMs,
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
        createdAtMs = 1L,
    )

    private class FakeScheduleDao : ScheduleDao {
        val schedules = linkedMapOf<String, ScheduleEntity>()

        override fun observeEnabledSchedules(): Flow<List<ScheduleEntity>> = emptyFlow()

        override suspend fun getById(scheduleId: String): ScheduleEntity? = schedules[scheduleId]

        override suspend fun getAllEnabled(): List<ScheduleEntity> = schedules.values.filter { it.enabled }

        override suspend fun findEnabledByStartAt(startAtMs: Long): List<ScheduleEntity> =
            schedules.values.filter { it.enabled && it.startAtMs == startAtMs }

        override suspend fun upsert(schedule: ScheduleEntity) {
            schedules[schedule.id] = schedule
        }

        override suspend fun disable(scheduleId: String) {
            val current = schedules[scheduleId] ?: return
            schedules[scheduleId] = current.copy(enabled = false)
        }
    }

    private fun clearScheduledAlarms(alarmManager: AlarmManager) {
        val shadow = shadowOf(alarmManager)
        shadow.scheduledAlarms.toList().forEach { alarm ->
            alarm.operation?.let(alarmManager::cancel)
        }
    }
}
