package com.ghostpin.app.data

import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MockLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PausedSimulationStoreTest {
    @Test
    fun `datastore paused simulation store round trips snapshot`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val store = DataStorePausedSimulationStore(context)
            val snapshot =
                PausedSimulationSnapshot(
                    config =
                        SimulationConfig(
                            profileName = "Car",
                            profileLookupKey = "builtin_car",
                            startLat = -23.0,
                            startLng = -46.0,
                            appMode = AppMode.JOYSTICK,
                        ),
                    pausedState =
                        SimulationState.Paused(
                            lastLocation = MockLocation(-23.0, -46.0),
                            profileName = "Car",
                            progressPercent = 0.4f,
                            lapProgressPercent = 0.2f,
                            currentLap = 2,
                            totalLapsLabel = "∞",
                            direction = -1,
                            elapsedTimeSec = 20,
                        ),
                    activeHistoryId = "history-1",
                    activeHistoryStartedAtMs = 1234L,
                )

            store.writeSnapshot(snapshot)

            assertEquals(snapshot, store.readSnapshot())

            store.writeSnapshot(null)
            assertNull(store.readSnapshot())
        }
}
