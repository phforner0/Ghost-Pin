package com.ghostpin.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.ghostpin.app.data.OnboardingDataStore
import com.ghostpin.app.service.SimulationService
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.maplibre.android.MapLibre

/** Single-activity entry point for GhostPin. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingDataStore: OnboardingDataStore
    private val viewModel: SimulationViewModel by viewModels()

    // Fix (🟡): The permission result callback was a no-op `{ /* no-op */ }`.
    // Now it triggers a Snackbar message when the user denies permissions,
    // preventing silent failure where the app would appear broken with no feedback.
    // The Snackbar is coordinated via a channel-style StateFlow read by GhostPinScreen.
    private val _permissionMessage = mutableStateOf<String?>(null)
    private val _lowMemorySignal = mutableIntStateOf(0)

    private val locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    results ->
                val denied =
                        results.entries.filter { !it.value }.map { it.key.substringAfterLast('.') }

                if (denied.isNotEmpty()) {
                    _permissionMessage.value =
                            "Location permission required for GPS simulation. " +
                                    "Please grant it in app settings."
                }
            }

    private val gpxFilePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let { viewModel.loadGpxFromUri(this, it) }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        requestPermissions()
        setContent {
            val isOnboardingComplete by
                    onboardingDataStore.isComplete.collectAsState(initial = null)

            GhostPinTheme {
                // Wait for DataStore to load before rendering to avoid UI flash
                val complete = isOnboardingComplete
                if (complete != null) {
                    AppNavHost(
                        viewModel = viewModel,
                        isOnboardingComplete = complete,
                        permissionMessage = _permissionMessage.value,
                        lowMemorySignal = _lowMemorySignal.intValue,
                        onPermissionMessageDismissed = { _permissionMessage.value = null },
                        onStartSimulation = ::startSimulation,
                        onStopSimulation = ::stopSimulation,
                        onPickGpxFile = { gpxFilePickerLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms =
                mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                        .also { list ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
        val needed =
                perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
        if (needed.isNotEmpty()) locationPermissionLauncher.launch(needed.toTypedArray())
    }

    private fun startSimulation(profile: MovementProfile, waypointPauseSec: Double = 0.0) {
        val intent =
                Intent(this, SimulationService::class.java).apply {
                    putExtra(SimulationService.EXTRA_PROFILE_NAME, profile.name)
                    putExtra(SimulationService.EXTRA_START_LAT, viewModel.startLat.value)
                    putExtra(SimulationService.EXTRA_START_LNG, viewModel.startLng.value)
                    putExtra(SimulationService.EXTRA_END_LAT, viewModel.endLat.value)
                    putExtra(SimulationService.EXTRA_END_LNG, viewModel.endLng.value)
                    putExtra(
                            SimulationService.EXTRA_FREQUENCY_HZ,
                            SimulationService.DEFAULT_FREQUENCY
                    )
                    // Sprint 6 — Task 23/24: inform the service about the current operating mode
                    putExtra(SimulationService.EXTRA_MODE, viewModel.selectedMode.value.name)
                    
                    val currentWaypoints = viewModel.waypoints.value
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LAT, currentWaypoints.map { it.lat }.toDoubleArray())
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LNG, currentWaypoints.map { it.lng }.toDoubleArray())
                    putExtra(SimulationService.EXTRA_WAYPOINT_PAUSE_SEC, waypointPauseSec)
                    putExtra(SimulationService.EXTRA_REPEAT_POLICY, viewModel.repeatPolicy.value.name)
                    putExtra(SimulationService.EXTRA_REPEAT_COUNT, viewModel.repeatCount.value)
                    putExtra(SimulationService.EXTRA_ROUTE_ID, viewModel.lastUsedConfig.value?.routeId)
                }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSimulation() {
        startService(
                Intent(this, SimulationService::class.java).apply {
                    action = SimulationService.ACTION_STOP
                }
        )
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _lowMemorySignal.intValue += 1
    }
}
