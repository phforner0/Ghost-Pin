package com.ghostpin.app.data

import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SimulationConfigStoreTest {
    @Test
    fun `datastore simulation config store round trips config`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val store = DataStoreSimulationConfigStore(context)
            val config =
                SimulationConfig(
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                    routeId = null,
                    appMode = AppMode.GPX,
                    waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                    waypointPauseSec = 3.0,
                    speedRatio = 0.8,
                    frequencyHz = 10,
                    repeatPolicy = RepeatPolicy.LOOP_N,
                    repeatCount = 2,
                )

            store.writeLastUsedConfig(config)

            assertEquals(config, store.readLastUsedConfig())

            store.writeLastUsedConfig(null)
            assertNull(store.readLastUsedConfig())
        }
}
