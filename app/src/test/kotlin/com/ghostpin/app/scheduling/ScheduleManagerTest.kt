package com.ghostpin.app.scheduling

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
