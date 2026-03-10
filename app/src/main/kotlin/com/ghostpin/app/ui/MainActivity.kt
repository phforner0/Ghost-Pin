package com.ghostpin.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.AndroidEntryPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/** Single-activity entry point for GhostPin. Uses Jetpack Compose for the entire UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: SimulationViewModel by viewModels()

    private val locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                // Handle permission results
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre before using MapView
        MapLibre.getInstance(this)

        requestPermissions()

        setContent {
            GhostPinTheme {
                GhostPinScreen(
                        viewModel = viewModel,
                        onStartSimulation = ::startSimulation,
                        onStopSimulation = ::stopSimulation,
                )
            }
        }
    }

    private fun requestPermissions() {
        val permissions =
                mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed =
                permissions.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
        if (needed.isNotEmpty()) {
            locationPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startSimulation(profile: MovementProfile) {
        val intent =
                Intent(this, SimulationService::class.java).apply {
                    putExtra(SimulationService.EXTRA_PROFILE_NAME, profile.name)
                    putExtra(SimulationService.EXTRA_START_LAT, viewModel.startLat.value)
                    putExtra(SimulationService.EXTRA_START_LNG, viewModel.startLng.value)
                    putExtra(SimulationService.EXTRA_END_LAT, viewModel.endLat.value)
                    putExtra(SimulationService.EXTRA_END_LNG, viewModel.endLng.value)
                }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSimulation() {
        val intent =
                Intent(this, SimulationService::class.java).apply {
                    action = SimulationService.ACTION_STOP
                }
        startService(intent)
    }
}

// ── Theme ──

@Composable
fun GhostPinTheme(content: @Composable () -> Unit) {
    val darkColorScheme =
            darkColorScheme(
                    primary = Color(0xFF80CBC4),
                    onPrimary = Color(0xFF003734),
                    primaryContainer = Color(0xFF00504D),
                    secondary = Color(0xFFB0BEC5),
                    surface = Color(0xFF121212),
                    onSurface = Color(0xFFE0E0E0),
                    background = Color(0xFF0A0A0A),
                    onBackground = Color(0xFFE0E0E0),
                    error = Color(0xFFCF6679),
            )

    MaterialTheme(
            colorScheme = darkColorScheme,
            content = content,
    )
}

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostPinScreen(
        viewModel: SimulationViewModel,
        onStartSimulation: (MovementProfile) -> Unit,
        onStopSimulation: () -> Unit,
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val simulationState by viewModel.simulationState.collectAsState()

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    "GhostPin",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                            )
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        titleContentColor = Color(0xFF80CBC4),
                                ),
                )
            },
            floatingActionButton = {
                val isRunning = simulationState is SimulationState.Running
                ExtendedFloatingActionButton(
                        onClick = {
                            if (isRunning) onStopSimulation()
                            else onStartSimulation(selectedProfile)
                        },
                        icon = {
                            Icon(
                                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                            )
                        },
                        text = { Text(if (isRunning) "Stop" else "Start") },
                        containerColor = if (isRunning) Color(0xFFCF6679) else Color(0xFF80CBC4),
                        contentColor = Color(0xFF121212),
                )
            },
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .background(
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                Color(0xFF0A0A0A),
                                                                Color(0xFF1A1A2E)
                                                        ),
                                        )
                                )
                                .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Status Card ──
            StatusCard(simulationState)

            // ── Profile Selector ──
            ProfileSelector(
                    profiles = viewModel.profiles,
                    selected = selectedProfile,
                    onSelect = viewModel::selectProfile,
            )

            // ── Interactive Map ──
            InteractiveMap(
                    viewModel = viewModel,
                    simulationState = simulationState,
                    modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun StatusCard(state: SimulationState) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = Color(0xFF1E1E2E),
                    ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint =
                                when (state) {
                                    is SimulationState.Running -> Color(0xFF80CBC4)
                                    is SimulationState.Error -> Color(0xFFCF6679)
                                    else -> Color(0xFF666666)
                                },
                        modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                            text =
                                    when (state) {
                                        is SimulationState.Idle -> "Idle"
                                        is SimulationState.Running ->
                                                "Simulating • ${state.profileName}"
                                        is SimulationState.Paused -> "Paused • ${state.profileName}"
                                        is SimulationState.Error -> "Error"
                                    },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color(0xFFE0E0E0),
                    )
                    if (state is SimulationState.Running) {
                        Text(
                                text =
                                        "Frame #${state.frameCount} • " +
                                                "${state.progressPercent.toInt()}% • " +
                                                "${state.elapsedTimeSec}s",
                                fontSize = 13.sp,
                                color = Color(0xFF888888),
                        )
                        Text(
                                text =
                                        "Lat: ${"%.6f".format(state.currentLocation.lat)}, " +
                                                "Lng: ${"%.6f".format(state.currentLocation.lng)}",
                                fontSize = 12.sp,
                                color = Color(0xFF80CBC4),
                        )
                    }
                    if (state is SimulationState.Error) {
                        Text(
                                text = state.message,
                                fontSize = 13.sp,
                                color = Color(0xFFCF6679),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSelector(
        profiles: List<MovementProfile>,
        selected: MovementProfile,
        onSelect: (MovementProfile) -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    "Movement Profile",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E0E0),
                    fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile == selected
                    FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(profile) },
                            label = {
                                Text(
                                        profile.name,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                )
                            },
                            colors =
                                    FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF80CBC4),
                                            selectedLabelColor = Color(0xFF121212),
                                    ),
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveMap(
        viewModel: SimulationViewModel,
        simulationState: SimulationState,
        modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    var mapController by remember { mutableStateOf<MapController?>(null) }

    // 🆕 Track whether start has been placed yet
    var startPlaced by remember { mutableStateOf(false) }

    val startLat by viewModel.startLat.collectAsState()
    val startLng by viewModel.startLng.collectAsState()
    val endLat by viewModel.endLat.collectAsState()
    val endLng by viewModel.endLng.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(simulationState, startLat, startLng, endLat, endLng) {
        val controller = mapController ?: return@LaunchedEffect
        when (simulationState) {
            is SimulationState.Idle -> {
                controller.clearPosition()
                controller.updateRoute(startLat, startLng, endLat, endLng)
            }
            is SimulationState.Running -> {
                controller.updateRoute(startLat, startLng, endLat, endLng)
                controller.updatePosition(simulationState.currentLocation)
            }
            is SimulationState.Paused -> controller.updatePosition(simulationState.lastLocation)
            is SimulationState.Error -> controller.clearPosition()
        }
    }

    Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                    factory = {
                        mapView.apply {
                            getMapAsync { mapLibreMap ->
                                mapController =
                                        MapController(mapLibreMap, context) { latLng ->
                                            // 🆕 Toggle logic: start → end → reset
                                            if (!startPlaced) {
                                                viewModel.setStartCoords(
                                                        latLng.latitude,
                                                        latLng.longitude
                                                )
                                                // Clear end to default (same as start) so route
                                                // isn't drawn yet
                                                viewModel.setEndCoords(
                                                        latLng.latitude,
                                                        latLng.longitude
                                                )
                                                startPlaced = true
                                            } else {
                                                viewModel.setEndCoords(
                                                        latLng.latitude,
                                                        latLng.longitude
                                                )
                                                startPlaced = false // ready for next route reset
                                            }
                                        }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
            )

            // 🆕 Helper text so the user knows what to do
            if (simulationState is SimulationState.Idle) {
                Text(
                        text =
                                if (!startPlaced) "Long-press to set Start"
                                else "Long-press to set End",
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp)
                                        .background(Color(0xCC1E1E2E), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFF80CBC4),
                        fontSize = 12.sp,
                )
            }
        }
    }
}
