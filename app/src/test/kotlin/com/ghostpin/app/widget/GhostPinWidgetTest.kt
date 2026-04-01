package com.ghostpin.app.widget

import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MockLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class GhostPinWidgetTest {
    @Test
    fun `renderModel uses stop action for active simulation`() {
        val model =
            GhostPinWidget.renderModel(
                SimulationState.Running(
                    currentLocation = MockLocation(1.0, 2.0),
                    profileName = "Car",
                    progressPercent = 0.42f,
                    elapsedTimeSec = 12,
                    frameCount = 34,
                )
            )

        assertEquals("Simulating", model.statusText)
        assertEquals("Car · 42%", model.secondaryText)
        assertEquals("Stop", model.toggleButtonText)
        assertEquals(SimulationService.ACTION_STOP, model.toggleAction)
    }

    @Test
    fun `renderModel uses last config start action when idle`() {
        val model = GhostPinWidget.renderModel(SimulationState.Idle)

        assertEquals("Stopped", model.statusText)
        assertEquals("—", model.secondaryText)
        assertEquals("Start", model.toggleButtonText)
        assertEquals(SimulationService.ACTION_START_LAST_CONFIG, model.toggleAction)
    }
}
