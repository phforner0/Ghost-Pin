package com.ghostpin.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

/**
 * GhostPin Application class.
 * Initializes Hilt DI and notification channels.
 */
@HiltAndroidApp
class GhostPinApp : Application() {

    companion object {
        const val CHANNEL_SIMULATION = "simulation_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_SIMULATION,
            "Simulation Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when a GPS simulation is running"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
