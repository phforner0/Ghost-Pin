package com.ghostpin.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.location.FakeLocationInjector
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Waypoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SimulationServiceInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var repository: SimulationRepository

    @Inject lateinit var fakeLocationInjector: FakeLocationInjector

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        fakeLocationInjector.reset()
        repository.reset()
        repository.emitOptionalConfig(null)
        createNotificationChannel()
        context.stopService(Intent(context, SimulationService::class.java))
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, SimulationService::class.java))
        repository.reset()
        repository.emitOptionalConfig(null)
        fakeLocationInjector.reset()
    }

    @Test
    fun startPauseResumeStop_gpxSimulationUpdatesRepositoryState() {
        val config =
            SimulationConfig(
                profileName = MovementProfile.PEDESTRIAN.name,
                profileLookupKey = MovementProfile.PEDESTRIAN.name,
                startLat = -23.0,
                startLng = -46.0,
                endLat = -22.999,
                endLng = -45.999,
                appMode = AppMode.GPX,
                waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.999, -45.999)),
                frequencyHz = 5,
                speedRatio = 1.0,
            )

        context.startForegroundService(SimulationService.createStartIntent(context, config))

        waitUntil(timeoutMs = 5_000) {
            repository.state.value is SimulationState.Running && fakeLocationInjector.injectedLocations.isNotEmpty()
        }
        assertTrue(fakeLocationInjector.registerCalls.get() > 0)

        context.startService(Intent(context, SimulationService::class.java).apply { action = SimulationService.ACTION_PAUSE })
        waitUntil(timeoutMs = 5_000) { repository.state.value is SimulationState.Paused }

        val injectedBeforeResume = fakeLocationInjector.injectedLocations.size
        context.startForegroundService(Intent(context, SimulationService::class.java).apply { action = SimulationService.ACTION_START })
        waitUntil(timeoutMs = 5_000) {
            repository.state.value is SimulationState.Running && fakeLocationInjector.injectedLocations.size > injectedBeforeResume
        }

        context.startService(Intent(context, SimulationService::class.java).apply { action = SimulationService.ACTION_STOP })
        waitUntil(timeoutMs = 5_000) {
            repository.state.value is SimulationState.Idle && fakeLocationInjector.unregisterCalls.get() > 0
        }
    }

    @Test
    fun actionSetProfile_updatesLastUsedConfig() {
        context.startService(
            Intent(context, SimulationService::class.java).apply {
                action = SimulationService.ACTION_SET_PROFILE
                putExtra(SimulationService.EXTRA_PROFILE_NAME, MovementProfile.CAR.name)
            }
        )

        waitUntil(timeoutMs = 3_000) { repository.lastUsedConfig.value?.profileName == MovementProfile.CAR.name }
        assertEquals(MovementProfile.CAR.name, repository.lastUsedConfig.value?.profileName)
    }

    @Test
    fun actionSetRoute_loadsRouteFromFileUri() {
        val routeFile = File(context.cacheDir, "instrumented-route.gpx")
        routeFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><name>Instrumented Route</name><trkseg>
                <trkpt lat="-23.0" lon="-46.0" />
                <trkpt lat="-22.999" lon="-45.999" />
              </trkseg></trk>
            </gpx>
            """.trimIndent()
        )

        context.startService(
            Intent(context, SimulationService::class.java).apply {
                action = SimulationService.ACTION_SET_ROUTE
                data = Uri.fromFile(routeFile)
            }
        )

        waitUntil(timeoutMs = 3_000) { repository.route.value != null }
        assertEquals("Instrumented Route", repository.route.value?.name)
        assertEquals(
            2,
            repository.route.value
                ?.waypoints
                ?.size
        )
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                GhostPinApp.CHANNEL_SIMULATION,
                "Simulation Active",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }
}
