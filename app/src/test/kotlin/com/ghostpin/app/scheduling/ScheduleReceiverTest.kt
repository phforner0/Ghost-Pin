package com.ghostpin.app.scheduling

import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MockLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReceiverTest {

    @Test
    fun `scheduled start is blocked for running paused and fetching states`() {
        assertFalse(
            shouldStartScheduledSimulation(
                SimulationState.Running(
                    currentLocation = MockLocation(1.0, 2.0),
                    profileName = "Car",
                    progressPercent = 0.2f,
                    elapsedTimeSec = 1,
                    frameCount = 1,
                )
            )
        )
        assertFalse(
            shouldStartScheduledSimulation(
                SimulationState.Paused(
                    lastLocation = MockLocation(1.0, 2.0),
                    profileName = "Car",
                    progressPercent = 0.2f,
                    elapsedTimeSec = 1,
                )
            )
        )
        assertFalse(shouldStartScheduledSimulation(SimulationState.FetchingRoute("Car")))
    }

    @Test
    fun `scheduled start is allowed from idle and error`() {
        assertTrue(shouldStartScheduledSimulation(SimulationState.Idle))
        assertTrue(shouldStartScheduledSimulation(SimulationState.Error("Oops")))
    }

    @Test
    fun `scheduled stop is only allowed for active states`() {
        assertTrue(
            shouldStopScheduledSimulation(
                SimulationState.Running(
                    currentLocation = MockLocation(1.0, 2.0),
                    profileName = "Car",
                    progressPercent = 0.2f,
                    elapsedTimeSec = 1,
                    frameCount = 1,
                )
            )
        )
        assertTrue(
            shouldStopScheduledSimulation(
                SimulationState.Paused(
                    lastLocation = MockLocation(1.0, 2.0),
                    profileName = "Car",
                    progressPercent = 0.2f,
                    elapsedTimeSec = 1,
                )
            )
        )
        assertTrue(shouldStopScheduledSimulation(SimulationState.FetchingRoute("Car")))
        assertFalse(shouldStopScheduledSimulation(SimulationState.Idle))
        assertFalse(shouldStopScheduledSimulation(SimulationState.Error("Oops")))
    }
}
