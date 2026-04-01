package com.ghostpin.app.scheduling

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BootCompletedReceiverInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var scheduleDao: ScheduleDao

    @Inject lateinit var scheduleManager: ScheduleManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun bootAndPackageReplaceActionsRearmEnabledSchedules() {
        val now = System.currentTimeMillis()
        val schedule =
            ScheduleEntity(
                id = "boot-test",
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
                waypointsJson = SimulationConfig.serializeWaypoints(emptyList()),
                waypointPauseSec = 0.0,
                speedRatio = 1.0,
                frequencyHz = 5,
                repeatPolicy = RepeatPolicy.NONE.name,
                repeatCount = 1,
                enabled = true,
                createdAtMs = now,
            )
        kotlinx.coroutines.runBlocking { scheduleDao.upsert(schedule) }

        val receiver = BootCompletedReceiver().apply { this.scheduleManager = this@BootCompletedReceiverInstrumentedTest.scheduleManager }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertTrue(kotlinx.coroutines.runBlocking { scheduleDao.getById(schedule.id) }?.enabled == true)
    }
}
