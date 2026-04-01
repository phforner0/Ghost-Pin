package com.ghostpin.app.scheduling

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCompletedReceiverTest {

    @Test
    fun `rearms schedules for boot completed`() {
        assertTrue(shouldRearmSchedulesForAction(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `rearms schedules for package replaced`() {
        assertTrue(shouldRearmSchedulesForAction(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `ignores unrelated action`() {
        assertFalse(shouldRearmSchedulesForAction(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }
}
