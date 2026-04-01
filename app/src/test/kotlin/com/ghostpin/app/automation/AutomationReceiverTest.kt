package com.ghostpin.app.automation

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ghostpin.app.service.SimulationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class AutomationReceiverTest {
    @Test
    fun `action start forwards extras to simulation service`() {
        val baseContext = RuntimeEnvironment.getApplication()
        val context = CapturingContext(baseContext)
        val receiver = AutomationReceiver()

        receiver.onReceive(
            context,
            Intent(AutomationReceiver.ACTION_START).apply {
                putExtra(AutomationReceiver.EXTRA_LAT, -23.0)
                putExtra(AutomationReceiver.EXTRA_LNG, -46.0)
                putExtra(AutomationReceiver.EXTRA_PROFILE_ID, "custom-profile-id")
                putExtra(AutomationReceiver.EXTRA_SPEED_RATIO, 0.75)
                putExtra(AutomationReceiver.EXTRA_FREQUENCY_HZ, 12)
            }
        )

        val startedIntent = context.lastForegroundServiceIntent
        requireNotNull(startedIntent)
        assertEquals(SimulationService.ACTION_START, startedIntent.action)
        assertEquals("custom-profile-id", startedIntent.getStringExtra(SimulationService.EXTRA_PROFILE_NAME))
        assertEquals("custom-profile-id", startedIntent.getStringExtra(SimulationService.EXTRA_PROFILE_LOOKUP_KEY))
        assertEquals(-23.0, startedIntent.getDoubleExtra(SimulationService.EXTRA_START_LAT, 0.0), 0.0)
        assertEquals(-46.0, startedIntent.getDoubleExtra(SimulationService.EXTRA_START_LNG, 0.0), 0.0)
        assertEquals(0.75, startedIntent.getDoubleExtra(SimulationService.EXTRA_SPEED_RATIO, 0.0), 0.0)
        assertEquals(12, startedIntent.getIntExtra(SimulationService.EXTRA_FREQUENCY_HZ, 0))
    }

    @Test
    fun `action set route rejects unsupported uri without starting service`() {
        val baseContext = RuntimeEnvironment.getApplication()
        val context = CapturingContext(baseContext)
        val receiver = AutomationReceiver()

        receiver.onReceive(
            context,
            Intent(AutomationReceiver.ACTION_SET_ROUTE).apply {
                putExtra(AutomationReceiver.EXTRA_ROUTE_FILE, "http://example.com/route.gpx")
            }
        )

        assertNull(context.lastServiceIntent)
        assertNull(context.lastForegroundServiceIntent)
    }

    @Test
    fun `action set route forwards content uri and read grant`() {
        val baseContext = RuntimeEnvironment.getApplication()
        val context = CapturingContext(baseContext)
        val receiver = AutomationReceiver()
        val routeUri = Uri.parse("content://com.ghostpin/routes/route-1")

        receiver.onReceive(
            context,
            Intent(AutomationReceiver.ACTION_SET_ROUTE).apply {
                putExtra(AutomationReceiver.EXTRA_ROUTE_FILE, routeUri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )

        val startedIntent = context.lastServiceIntent
        requireNotNull(startedIntent)
        assertEquals(SimulationService.ACTION_SET_ROUTE, startedIntent.action)
        assertEquals(routeUri, startedIntent.data)
        assertTrue((startedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    private class CapturingContext(
        base: Context
    ) : ContextWrapper(base) {
        var lastServiceIntent: Intent? = null
            private set
        var lastForegroundServiceIntent: Intent? = null
            private set

        override fun startService(service: Intent): ComponentName? {
            lastServiceIntent = service
            return null
        }

        override fun startForegroundService(service: Intent): ComponentName? {
            lastForegroundServiceIntent = service
            return null
        }
    }
}
