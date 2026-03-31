package com.ghostpin.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.ghostpin.app.data.ProfileManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GhostPin Application class.
 *
 * Sprint 4 addition: seeds the 5 built-in movement profiles into Room
 * on first launch (idempotent — [ProfileManager.seedBuiltInsIfNeeded] is a no-op
 * if the profiles already exist).
 */
@HiltAndroidApp
class GhostPinApp : Application() {
    companion object {
        const val CHANNEL_SIMULATION = "simulation_channel"
    }

    // Application-scoped coroutine scope for one-off init tasks.
    // Uses SupervisorJob so a failed child doesn't cancel siblings.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var profileManager: ProfileManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        seedProfiles()
    }

    private fun seedProfiles() {
        appScope.launch {
            profileManager.seedBuiltInsIfNeeded()
        }
    }

    private fun createNotificationChannels() {
        val channel =
            NotificationChannel(
                CHANNEL_SIMULATION,
                "Simulation Active",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when a GPS simulation is running"
                setShowBadge(false)
            }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
