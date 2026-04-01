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
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.data.OnboardingDataStore
import com.ghostpin.app.routing.RouteImportValidator
import com.ghostpin.app.service.SimulationService
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import javax.inject.Inject

/** Single-activity entry point for GhostPin. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var onboardingDataStore: OnboardingDataStore
    private val viewModel: SimulationViewModel by viewModels()

    // Fix (🟡): The permission result callback was a no-op `{ /* no-op */ }`.
    // Now it triggers a Snackbar message when the user denies permissions,
    // preventing silent failure where the app would appear broken with no feedback.
    // The Snackbar is coordinated via a channel-style StateFlow read by GhostPinScreen.
    private val permissionMessageState = mutableStateOf<String?>(null)
    private val lowMemorySignalState = mutableIntStateOf(0)
    private val pendingImportedRouteUriState = mutableStateOf<Uri?>(null)
    private var pendingRouteExport: Pair<String, String>? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied =
                results.entries.filter { !it.value }.map { it.key.substringAfterLast('.') }

            if (denied.isNotEmpty()) {
                permissionMessageState.value =
                    "Location permission required for GPS simulation. " +
                    "Please grant it in app settings."
            }
        }

    private val gpxFilePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                RouteImportValidator
                    .validateUri(it)
                    .onSuccess { validUri ->
                        RouteImportValidator
                            .persistReadGrantIfNeeded(
                                contentResolver,
                                validUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            ).onFailure {
                                permissionMessageState.value =
                                    "Route imported, but Android did not grant persistent file access. Re-import may be needed later."
                            }
                        viewModel.loadGpxFromUri(this, validUri)
                    }.onFailure { error ->
                        permissionMessageState.value = error.message ?: "Failed to open route file."
                    }
            }
        }

    private val routeFileImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                runCatching {
                    RouteImportValidator.validateUri(uri).getOrThrow()
                }.onSuccess { validUri ->
                    RouteImportValidator
                        .persistReadGrantIfNeeded(
                            contentResolver,
                            validUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        ).onFailure {
                            permissionMessageState.value =
                                "Route imported, but Android did not grant persistent file access. Re-import may be needed later."
                        }
                    pendingImportedRouteUriState.value = validUri
                }.onFailure { error ->
                    permissionMessageState.value = error.message ?: "Failed to import route file."
                }
            }
        }

    private val routeFileExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            val export = pendingRouteExport
            pendingRouteExport = null
            if (uri == null || export == null) return@registerForActivityResult

            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            writer.write(export.second)
                        } ?: error("Cannot open destination for route export")
                    }
                }.onFailure { error ->
                    permissionMessageState.value = error.message ?: "Failed to export route file."
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            val isOnboardingComplete by
                onboardingDataStore.isComplete.collectAsState(initial = null)

            GhostPinTheme {
                // Wait for DataStore to load before rendering to avoid UI flash
                val complete = isOnboardingComplete
                if (complete != null) {
                    LaunchedEffect(complete) {
                        if (complete) requestPermissions()
                    }
                    AppNavHost(
                        viewModel = viewModel,
                        isOnboardingComplete = complete,
                        permissionMessage = permissionMessageState.value,
                        lowMemorySignal = lowMemorySignalState.intValue,
                        onPermissionMessageDismissed = { permissionMessageState.value = null },
                        onStartSimulation = ::startSimulation,
                        onStopSimulation = ::stopSimulation,
                        onPickGpxFile = { gpxFilePickerLauncher.launch(arrayOf("*/*")) },
                        onImportRouteFile = { routeFileImportLauncher.launch(arrayOf("*/*")) },
                        onExportRouteFile = { filename, content ->
                            pendingRouteExport = filename to content
                            routeFileExportLauncher.launch(filename)
                        },
                        pendingImportedRouteUri = pendingImportedRouteUriState.value,
                        onImportedRouteConsumed = { pendingImportedRouteUriState.value = null },
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
            ).also { list ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        val needed =
            perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (needed.isNotEmpty()) locationPermissionLauncher.launch(needed.toTypedArray())
    }

    private fun startSimulation(
        profile: MovementProfile,
        waypointPauseSec: Double = 0.0
    ) {
        val config = viewModel.buildCurrentConfig(profile = profile, waypointPauseSec = waypointPauseSec)
        val intent = SimulationService.createStartIntent(this, config)
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
        lowMemorySignalState.intValue += 1
    }
}
