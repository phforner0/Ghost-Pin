package com.ghostpin.app.ui

import com.ghostpin.core.model.MovementProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHeuristicsTest {
    @Test
    fun `drone profile surfaces high speed heuristic`() {
        val heuristics = profileHeuristics(MovementProfile.DRONE)
        assertTrue(heuristics.contains("High speed"))
    }

    @Test
    fun `pedestrian profile surfaces stable heuristic`() {
        val heuristics = profileHeuristics(MovementProfile.PEDESTRIAN)
        assertTrue(heuristics.contains("Stable"))
    }

    @Test
    fun `high multipath profile surfaces multipath heuristic`() {
        val heuristics = profileHeuristics(MovementProfile.CAR.copy(pMultipath = 0.08))
        assertTrue(heuristics.contains("High multipath"))
    }
}
