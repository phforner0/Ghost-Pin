package com.ghostpin.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ghostpin.app.service.SimulationService
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.security.LogSanitizer

/**
 * BroadcastReceiver for external automation of GhostPin via ADB or Tasker.
 *
 * Protected by `com.ghostpin.permission.AUTOMATION` (normal protection level)
 * to prevent unauthorised apps from controlling the simulation.
 *
 * Supported actions:
 *   - `com.ghostpin.ACTION_START`       — Start simulation
 *   - `com.ghostpin.ACTION_STOP`        — Stop simulation
 *   - `com.ghostpin.ACTION_PAUSE`       — Pause simulation
 *   - `com.ghostpin.ACTION_SET_ROUTE`   — Load a route from GPX/KML/TCX file URI
 *   - `com.ghostpin.ACTION_SET_PROFILE` — Change the active movement profile
 *
 * See AUTOMATION.md in the repository root for full usage examples.
 */
class AutomationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationReceiver"

        const val ACTION_START       = "com.ghostpin.ACTION_START"
        const val ACTION_STOP        = "com.ghostpin.ACTION_STOP"
        const val ACTION_PAUSE       = "com.ghostpin.ACTION_PAUSE"
        const val ACTION_SET_ROUTE   = "com.ghostpin.ACTION_SET_ROUTE"
        const val ACTION_SET_PROFILE = "com.ghostpin.ACTION_SET_PROFILE"

        // Extras
        const val EXTRA_LAT          = "EXTRA_LAT"
        const val EXTRA_LNG          = "EXTRA_LNG"
        const val EXTRA_SPEED_RATIO  = "EXTRA_SPEED_RATIO"
        const val EXTRA_FREQUENCY_HZ = "EXTRA_FREQUENCY_HZ"
        const val EXTRA_PROFILE_ID   = "EXTRA_PROFILE_ID"
        const val EXTRA_ROUTE_FILE   = "EXTRA_ROUTE_FILE"

        fun buildStartServiceIntent(context: Context, sourceIntent: Intent): Intent {
            return Intent(context, SimulationService::class.java).apply {
                action = SimulationService.ACTION_START

                if (sourceIntent.hasExtra(EXTRA_LAT) && sourceIntent.hasExtra(EXTRA_LNG)) {
                    val lat = sourceIntent.getDoubleExtra(EXTRA_LAT, 0.0).coerceIn(-90.0, 90.0)
                    val lng = sourceIntent.getDoubleExtra(EXTRA_LNG, 0.0).coerceIn(-180.0, 180.0)
                    putExtra(SimulationService.EXTRA_START_LAT, lat)
                    putExtra(SimulationService.EXTRA_START_LNG, lng)
                }

                if (sourceIntent.hasExtra(EXTRA_PROFILE_ID)) {
                    val profileId = sourceIntent.getStringExtra(EXTRA_PROFILE_ID) ?: "Car"
                    val validProfile = if (MovementProfile.BUILT_IN.containsKey(profileId)) {
                        profileId
                    } else {
                        Log.w(TAG, "Unknown profile '${LogSanitizer.sanitizeString(profileId)}', defaulting to Car")
                        "Car"
                    }
                    putExtra(SimulationService.EXTRA_PROFILE_NAME, validProfile)
                }

                if (sourceIntent.hasExtra(EXTRA_SPEED_RATIO)) {
                    val ratio = sourceIntent.getDoubleExtra(EXTRA_SPEED_RATIO, 0.65).coerceIn(0.0, 1.0)
                    putExtra(SimulationService.EXTRA_SPEED_RATIO, ratio)
                }

                if (sourceIntent.hasExtra(EXTRA_FREQUENCY_HZ)) {
                    val freq = sourceIntent.getIntExtra(EXTRA_FREQUENCY_HZ, 5).coerceIn(1, 60)
                    putExtra(SimulationService.EXTRA_FREQUENCY_HZ, freq)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Received action: ${LogSanitizer.sanitizeString(action)}")

        when (action) {
            ACTION_START -> handleStart(context, intent)
            ACTION_STOP  -> handleStop(context)
            ACTION_PAUSE -> handlePause(context)
            ACTION_SET_ROUTE   -> handleSetRoute(context, intent)
            ACTION_SET_PROFILE -> handleSetProfile(context, intent)
            else -> Log.w(TAG, "Unknown action: ${LogSanitizer.sanitizeString(action)}")
        }
    }

    private fun handleStart(context: Context, intent: Intent) {
        context.startForegroundService(buildStartServiceIntent(context, intent))
    }

    private fun handleStop(context: Context) {
        val intent = Intent(context, SimulationService::class.java).apply {
            action = SimulationService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun handlePause(context: Context) {
        val intent = Intent(context, SimulationService::class.java).apply {
            action = SimulationService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    private fun handleSetRoute(context: Context, intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_ROUTE_FILE)
        if (uriString.isNullOrBlank()) {
            Log.w(TAG, "ACTION_SET_ROUTE missing EXTRA_ROUTE_FILE")
            return
        }

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid route file URI: ${LogSanitizer.sanitizeString(uriString)}")
            return
        }

        val serviceIntent = Intent(context, SimulationService::class.java).apply {
            action = SimulationService.ACTION_SET_ROUTE
            data = uri
        }
        context.startService(serviceIntent)
    }

    private fun handleSetProfile(context: Context, intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId.isNullOrBlank()) {
            Log.w(TAG, "ACTION_SET_PROFILE missing EXTRA_PROFILE_ID")
            return
        }

        if (!MovementProfile.BUILT_IN.containsKey(profileId)) {
            Log.w(TAG, "Unknown profile: ${LogSanitizer.sanitizeString(profileId)}")
            return
        }

        val serviceIntent = Intent(context, SimulationService::class.java).apply {
            action = SimulationService.ACTION_SET_PROFILE
            putExtra(SimulationService.EXTRA_PROFILE_NAME, profileId)
        }
        context.startService(serviceIntent)
    }
}
