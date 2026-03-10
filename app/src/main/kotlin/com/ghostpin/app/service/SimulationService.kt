package com.ghostpin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.R
import com.ghostpin.app.location.MockLocationInjector
import com.ghostpin.app.ui.MainActivity
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.engine.noise.LayeredNoiseModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service running the GPS simulation loop.
 *
 * Runs at configurable Hz (default 1 Hz), applying the full noise pipeline
 * to each interpolated position and injecting it via [MockLocationInjector].
 */
@AndroidEntryPoint
class SimulationService : LifecycleService() {

    @Inject
    lateinit var mockLocationInjector: MockLocationInjector

    private var noiseModel: LayeredNoiseModel? = null
    private var simulationJob: Job? = null

    private val _state = MutableStateFlow<SimulationState>(SimulationState.Idle)
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    companion object {
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_START_LAT = "start_lat"
        const val EXTRA_START_LNG = "start_lng"
        const val EXTRA_END_LAT = "end_lat"
        const val EXTRA_END_LNG = "end_lng"
        const val EXTRA_FREQUENCY_HZ = "frequency_hz"
        const val ACTION_STOP = "com.ghostpin.action.STOP"
        const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSimulation()
            return START_NOT_STICKY
        }

        // Guard: mock provider must be enabled for this build flavor
        if (!BuildConfig.MOCK_PROVIDER_ENABLED) {
            _state.value = SimulationState.Error("Mock provider not available in this build.")
            stopSelf()
            return START_NOT_STICKY
        }

        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Pedestrian"
        val startLat = intent?.getDoubleExtra(EXTRA_START_LAT, -23.5505) ?: -23.5505
        val startLng = intent?.getDoubleExtra(EXTRA_START_LNG, -46.6333) ?: -46.6333
        val endLat = intent?.getDoubleExtra(EXTRA_END_LAT, -23.5510) ?: -23.5510
        val endLng = intent?.getDoubleExtra(EXTRA_END_LNG, -46.6340) ?: -46.6340
        val frequencyHz = intent?.getIntExtra(EXTRA_FREQUENCY_HZ, 1) ?: 1

        val profile = MovementProfile.BUILT_IN[profileName] ?: MovementProfile.PEDESTRIAN

        startForeground(NOTIFICATION_ID, buildNotification(profile.name))
        startSimulation(profile, startLat, startLng, endLat, endLng, frequencyHz)

        return START_STICKY
    }

    private fun startSimulation(
        profile: MovementProfile,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        frequencyHz: Int,
    ) {
        // Reset any previous simulation
        simulationJob?.cancel()
        noiseModel?.reset()

        noiseModel = LayeredNoiseModel.fromProfile(profile)
        val intervalMs = 1000L / frequencyHz
        val deltaTimeSec = 1.0 / frequencyHz

        simulationJob = lifecycleScope.launch {
            try {
                mockLocationInjector.registerProvider()

                var frameCount = 0L
                val startTimeMs = System.currentTimeMillis()

                // Simple linear interpolation between start and end for now.
                // Will be replaced with RouteInterpolator in Phase 2.
                val totalFrames = 600L  // ~10 min at 1 Hz
                var progress = 0f

                while (isActive && progress <= 1f) {
                    progress = (frameCount.toFloat() / totalFrames).coerceIn(0f, 1f)

                    val currentLat = startLat + (endLat - startLat) * progress
                    val currentLng = startLng + (endLng - startLng) * progress

                    val rawLocation = MockLocation(
                        lat = currentLat,
                        lng = currentLng,
                        altitude = 760.0,
                        speed = (profile.maxSpeedMs * 0.5).toFloat(),
                        bearing = 180f,
                        accuracy = 10f,
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                        timestampMs = System.currentTimeMillis(),
                    )

                    val noisyLocation = noiseModel!!.applyToLocation(rawLocation, deltaTimeSec)
                    mockLocationInjector.inject(noisyLocation)

                    _state.value = SimulationState.Running(
                        currentLocation = noisyLocation,
                        profileName = profile.name,
                        progressPercent = progress * 100f,
                        elapsedTimeSec = (System.currentTimeMillis() - startTimeMs) / 1000,
                        frameCount = frameCount,
                    )

                    frameCount++
                    delay(intervalMs)
                }

                // Simulation complete
                _state.value = SimulationState.Idle
                stopSelf()

            } catch (e: Exception) {
                _state.value = SimulationState.Error(e.message ?: "Unknown error")
                stopSelf()
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        noiseModel?.reset()
        noiseModel = null

        try {
            mockLocationInjector.unregisterProvider()
        } catch (_: Exception) { }

        _state.value = SimulationState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSimulation()
        super.onDestroy()
    }

    private fun buildNotification(profileName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SimulationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GhostPinApp.CHANNEL_SIMULATION)
            .setContentTitle(getString(R.string.notification_simulation_title))
            .setContentText(getString(R.string.notification_simulation_text, profileName))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .build()
    }
}
