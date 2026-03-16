package com.ghostpin.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ghostpin.core.model.distanceMeters
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ghostpin.app.data.OnboardingDataStore
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.app.ui.onboarding.OnboardingScreen
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

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
                when (isOnboardingComplete) {
                    null -> {
                        // Loading state — wait for DataStore read to finish before rendering either
                        // to avoid a disruptive UI flash or heavy MapLibre initialization.
                    }
                    true -> {
                        LaunchedEffect(Unit) { viewModel.initializeLocation(this@MainActivity) }
                        GhostPinScreen(
                                viewModel = viewModel,
                                permissionMessage = _permissionMessage.value,
                                onPermissionMessageDismissed = { _permissionMessage.value = null },
                                onStartSimulation = ::startSimulation,
                                onStopSimulation = ::stopSimulation,
                                onPickGpxFile = { gpxFilePickerLauncher.launch(arrayOf("*/*")) },
                        )
                    }
                    false -> {
                        OnboardingScreen(
                                onComplete = { profileName, lat, lng ->
                                    viewModel.selectProfile(
                                            MovementProfile.BUILT_IN[profileName]
                                                    ?: MovementProfile.CAR
                                    )
                                    viewModel.setStartLat(lat)
                                    viewModel.setStartLng(lng)
                                    // Do NOT auto-start simulation here. Wait for user to
                                    // explicitly click "Start"
                                    // in the main UI, so we don't unexpectedly jump to default
                                    // coordinates.
                                }
                        )
                    }
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

    private fun startSimulation(profile: MovementProfile) {
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
}

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun GhostPinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme =
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
                    ),
            content = content,
    )
}

// ── Main Screen ──────────────────────────────────────────────────────────────

/**
 * Root composable that collects all ViewModels StateFlows and distributes them as plain values to
 * child composables.
 *
 * Fix (🟡): Child composables (InteractiveMap, SimulationStatusCard, ProfileSelector) now receive
 * data + callbacks instead of the ViewModel directly. This makes them independently previewable,
 * testable, and reusable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostPinScreen(
        viewModel: SimulationViewModel,
        permissionMessage: String?,
        onPermissionMessageDismissed: () -> Unit,
        onStartSimulation: (MovementProfile) -> Unit,
        onStopSimulation: () -> Unit,
        onPickGpxFile: () -> Unit = {}, // ← novo param
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val simulationState by viewModel.simulationState.collectAsState()
    // Fix (🟡): isBusy now comes from ViewModel, not computed here
    val isBusy by viewModel.isBusy.collectAsState()
    val startLat by viewModel.startLat.collectAsState()
    val startLng by viewModel.startLng.collectAsState()
    val endLat by viewModel.endLat.collectAsState()
    val endLng by viewModel.endLng.collectAsState()
    val route by viewModel.route.collectAsState()
    val startPlaced by viewModel.startPlaced.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val deviceLocation by viewModel.deviceLocation.collectAsState()

    // Show Snackbar when permissions are denied
    LaunchedEffect(permissionMessage) {
        if (permissionMessage != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                        message = permissionMessage,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long,
                )
                onPermissionMessageDismissed()
            }
        }
    }

    // Return map to the actual physical location whenever simulation is fully stopped
    LaunchedEffect(simulationState) {
        if (simulationState is SimulationState.Idle) {
            viewModel.initializeLocation(context)
        }
    }

    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                        title = {
                            Text("GhostPin", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        titleContentColor = Color(0xFF80CBC4),
                                ),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                        onClick = {
                            if (isBusy) onStopSimulation() else onStartSimulation(selectedProfile)
                        },
                        containerColor = if (isBusy) Color(0xFFCF6679) else Color(0xFF80CBC4),
                        contentColor = Color(0xFF003734),
                        icon = {
                            Icon(
                                    imageVector =
                                            if (isBusy) Icons.Default.Stop
                                            else Icons.Default.PlayArrow,
                                    contentDescription = null,
                            )
                        },
                        text = {
                            Text(if (isBusy) "Stop" else "Start", fontWeight = FontWeight.Bold)
                        },
                )
            },
            bottomBar = {
                NavigationBar(
                        containerColor = Color(0xFF1E1E2E),
                        contentColor = Color(0xFF80CBC4)
                ) {
                    AppMode.entries.forEach { mode ->
                        NavigationBarItem(
                                selected = selectedMode == mode,
                                onClick = { viewModel.setAppMode(mode) },
                                icon = {
                                    when (mode) {
                                        AppMode.CLASSIC ->
                                                Icon(
                                                        Icons.Default.LocationOn,
                                                        contentDescription = null
                                                )
                                        AppMode.JOYSTICK ->
                                                Icon(
                                                        Icons.Default.Gamepad,
                                                        contentDescription = null
                                                )
                                        AppMode.WAYPOINTS ->
                                                Icon(Icons.Default.Map, contentDescription = null)
                                        AppMode.GPX ->
                                                Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = null
                                                )
                                    }
                                },
                                label = { Text(mode.displayName, fontSize = 10.sp) },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color(0xFF003734),
                                                selectedTextColor = Color(0xFF80CBC4),
                                                indicatorColor = Color(0xFF80CBC4),
                                                unselectedIconColor = Color(0xFFB0BEC5),
                                                unselectedTextColor = Color(0xFFB0BEC5)
                                        )
                        )
                    }
                }
            },
            containerColor = Color(0xFF0A0A0A),
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val waypoints by viewModel.waypoints.collectAsState()

            // Map with overlays — state hoisted; no viewModel reference inside
            InteractiveMap(
                    startLat = startLat,
                    startLng = startLng,
                    endLat = endLat,
                    endLng = endLng,
                    waypoints = waypoints,
                    appMode = selectedMode,
                    route = route,
                    startPlaced = startPlaced,
                    simulationState = simulationState,
                    deviceLocation = deviceLocation,
                    onMapLongPress = viewModel::onMapLongPress,
                    modifier = Modifier.fillMaxWidth().weight(1f),
            )

            SimulationStatusCard(state = simulationState)

            when (selectedMode) {
                AppMode.CLASSIC -> {
                    ClassicModePanel(
                            profiles = viewModel.profiles,
                            selectedProfile = selectedProfile,
                            enabled = !isBusy,
                            onSelect = viewModel::selectProfile,
                    )
                }
                AppMode.JOYSTICK -> JoystickModePanel()
                AppMode.WAYPOINTS -> {
                    WaypointsModePanel(
                        waypoints = waypoints,
                        onRemoveWaypoint = viewModel::removeWaypoint,
                        onClearWaypoints = viewModel::clearWaypoints,
                        profiles = viewModel.profiles,
                        selectedProfile = selectedProfile,
                        enabled = !isBusy,
                        onSelectProfile = viewModel::selectProfile,
                        onStart = { startSimulation(selectedProfile) }
                    )
                }
                AppMode.GPX -> {
                    val gpxLoadState by viewModel.gpxLoadState.collectAsState()
                    GpxModePanel(
                            gpxLoadState = gpxLoadState,
                            onPickFile = onPickGpxFile,
                            onClearRoute = viewModel::clearGpxRoute,
                    )
                }
            }

            Spacer(Modifier.height(16.dp)) // FAB clearance inside scaffold
        }
    }
}

// ── Mode Panels ─────────────────────────────────────────────────────────────

@Composable
fun ClassicModePanel(
        profiles: List<MovementProfile>,
        selectedProfile: MovementProfile,
        enabled: Boolean,
        onSelect: (MovementProfile) -> Unit
) {
    ProfileSelector(
            profiles = profiles,
            selectedProfile = selectedProfile,
            enabled = enabled,
            onSelect = onSelect,
    )
}

@Composable
fun JoystickModePanel() {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Joystick Navigation",
                    color = Color(0xFF80CBC4),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
            )
            Text(
                    text =
                            "Press Start, then switch to your game. A floating joystick will let you walk in any direction in real-time.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun WaypointsModePanel(
        waypoints: List<Waypoint>,
        onRemoveWaypoint: (Int) -> Unit,
        onClearWaypoints: () -> Unit,
        profiles: List<MovementProfile>,
        selectedProfile: MovementProfile,
        enabled: Boolean,
        onSelectProfile: (MovementProfile) -> Unit,
        onStart: () -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1C)),
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                        text = "Waypoints Mode",
                        color = Color(0xFF80CBC4),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                )
                Text(
                        text = "Long press on the map to add stops to your route.",
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                )
            }

            if (waypoints.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearWaypoints,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear all waypoints")
                }
            }

            // Waypoints List
            if (waypoints.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    waypoints.forEachIndexed { index, wp ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${String.format("%.4f", wp.lat)}, ${String.format("%.4f", wp.lng)}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = { onRemoveWaypoint(index) }, enabled = enabled) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFEF5350))
                            }
                        }
                    }
                }
            }

            ProfileSelector(
                    profiles = profiles,
                    selectedProfile = selectedProfile,
                    enabled = enabled,
                    onSelect = onSelectProfile,
            )

            Button(
                    onClick = onStart,
                    enabled = enabled && waypoints.size >= 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))
            ) {
                Text("Start Multi-Stop Route", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GpxModePanel(
        gpxLoadState: SimulationViewModel.GpxLoadState,
        onPickFile: () -> Unit,
        onClearRoute: () -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF80CBC4),
                        modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                        text = "GPX Import",
                        color = Color(0xFF80CBC4),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                )
            }

            // State-driven content
            when (gpxLoadState) {
                // ── Idle: show file picker button ──────────────────────────
                is SimulationViewModel.GpxLoadState.Idle -> {
                    Text(
                            text =
                                    "Select a .gpx file from your device. The route will be followed exactly, bypassing OSRM.",
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp,
                    )
                    Button(
                            onClick = onPickFile,
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00504D),
                                            contentColor = Color(0xFF80CBC4),
                                    ),
                            shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                                Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Choose GPX File", fontWeight = FontWeight.Medium)
                    }
                }

                // ── Loading ────────────────────────────────────────────────
                is SimulationViewModel.GpxLoadState.Loading -> {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF80CBC4),
                                strokeWidth = 2.dp,
                        )
                        Text("Parsing GPX file…", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                    }
                }

                // ── Success: show route summary + clear option ──────────────
                is SimulationViewModel.GpxLoadState.Success -> {
                    val route = gpxLoadState.route
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF003734),
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = route.name,
                                        color = Color(0xFF80CBC4),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                )
                                Text(
                                        text =
                                                "${route.waypoints.size} pts · ${"%.1f".format(route.distanceMeters / 1000)} km",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = onClearRoute) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove route",
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                            text = "✓ Route loaded. Press Start to begin GPX playback.",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                    )
                }

                // ── Error ──────────────────────────────────────────────────
                is SimulationViewModel.GpxLoadState.Error -> {
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF3B1A1A),
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFCF6679),
                                    modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                    text = gpxLoadState.message,
                                    color = Color(0xFFCF6679),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    TextButton(onClick = onPickFile) {
                        Text("Try another file", color = Color(0xFF80CBC4), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Map composable ────────────────────────────────────────────────────────────

/**
 * Renders the MapLibre map with route, pins and overlay indicators.
 *
 * Fix (🟡): Accepts plain data parameters instead of the ViewModel reference. Fix (🔴):
 * AnimatedVisibility is now correctly wrapped in [Column] to provide the [ColumnScope] required by
 * this extension function. The `?.let { }` lambda is replaced by `if (x != null) { }` which
 * maintains the @Composable context.
 */
@Composable
fun InteractiveMap(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        waypoints: List<Waypoint>,
        appMode: AppMode,
        route: Route?,
        startPlaced: Boolean,
        simulationState: SimulationState,
        deviceLocation: Pair<Double, Double>?,
        onMapLongPress: (Double, Double) -> Unit,
        modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    var mapController by remember { mutableStateOf<MapController?>(null) }

    // Manage MapView lifecycle
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

    // Center map on real device location as soon as it becomes available.
    // Uses moveTo() (single-point) instead of updateRoute() (two-point fitCamera)
    // to avoid the IllegalArgumentException when start == end coordinates.
    LaunchedEffect(deviceLocation) {
        val loc = deviceLocation ?: return@LaunchedEffect
        val controller = mapController ?: return@LaunchedEffect
        controller.moveTo(loc.first, loc.second, zoom = 15.0)
    }

    // Update map whenever relevant state changes
    LaunchedEffect(simulationState, startLat, startLng, endLat, endLng, waypoints, appMode, route) {
        val controller = mapController ?: return@LaunchedEffect
        when (simulationState) {
            is SimulationState.Idle, is SimulationState.FetchingRoute -> {
                controller.clearPosition()
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else if (appMode == AppMode.WAYPOINTS) controller.updateWaypoints(waypoints)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
            }
            is SimulationState.Running -> {
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else if (appMode == AppMode.WAYPOINTS) controller.updateWaypoints(waypoints)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
                controller.updatePosition(simulationState.currentLocation)
            }
            is SimulationState.Paused -> controller.updatePosition(simulationState.lastLocation)
            is SimulationState.Error -> controller.clearPosition()
        }
    }

    // Hint text: tells user what the next long-press will do
    val hintText: String? =
            when {
                simulationState is SimulationState.Running -> null
                simulationState is SimulationState.FetchingRoute -> null
                !startPlaced -> "Long-press to set Start"
                else -> "Long-press to set End"
            }

    Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                    factory = {
                        mapView.apply {
                            getMapAsync { mapLibreMap ->
                                mapController =
                                        MapController(mapLibreMap) { latLng ->
                                            onMapLongPress(latLng.latitude, latLng.longitude)
                                        }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
            )

            // Fix (🔴): AnimatedVisibility is an extension on ColumnScope.
            // Placing it directly inside Box provided BoxScope instead, causing
            // the compiler error:
            //   "fun ColumnScope.AnimatedVisibility(...) cannot be called in this
            //    context with an implicit receiver."
            //
            // Fix: wrap each AnimatedVisibility in a Column that provides ColumnScope.
            // The modifier.align() is moved to the Column, not AnimatedVisibility,
            // since BoxScope.align() is available on the Column inside the Box.
            //
            // Fix (🔴): The `hintText?.let { text -> Surface(...) }` lambda was not
            // a @Composable function — calling Surface/Text inside it caused:
            //   "@Composable invocations can only happen from the context of a @Composable
            // function"
            // Replaced with `if (hintText != null) { }` which preserves @Composable context.

            // Hint overlay (bottom-center)
            Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                AnimatedVisibility(
                        visible = hintText != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                ) {
                    if (hintText != null) {
                        Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xCC1A1A2E),
                        ) {
                            Text(
                                    text = hintText,
                                    color = Color(0xFF80CBC4),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Loading overlay (top-end) — shown while OSRM fetches the route
            Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                AnimatedVisibility(
                        visible = simulationState is SimulationState.FetchingRoute,
                        enter = fadeIn(),
                        exit = fadeOut(),
                ) {
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC1A1A2E),
                    ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color(0xFF80CBC4),
                                    strokeWidth = 2.dp,
                            )
                            Text("Fetching route…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Status Card ───────────────────────────────────────────────────────────────

/** Fix (🟡): Accepts [SimulationState] directly — no ViewModel reference needed. */
@Composable
fun SimulationStatusCard(state: SimulationState) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint =
                            when (state) {
                                is SimulationState.Running -> Color(0xFF80CBC4)
                                is SimulationState.FetchingRoute -> Color(0xFFFFB300)
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
                                    is SimulationState.FetchingRoute ->
                                            "Fetching route… • ${state.profileName}"
                                    is SimulationState.Running ->
                                            "Simulating • ${state.profileName}"
                                    is SimulationState.Paused -> "Paused • ${state.profileName}"
                                    is SimulationState.Error -> "Error"
                                },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFFE0E0E0),
                )
                if (state is SimulationState.Running) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text =
                                    "Frame #${state.frameCount} · " +
                                            "${state.progressPercent.toInt()}% · " +
                                            "${state.elapsedTimeSec}s",
                            fontSize = 13.sp,
                            color = Color(0xFF888888),
                    )
                }
                if (state is SimulationState.Error) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = Color(0xFFCF6679),
                    )
                }
            }
        }
    }
}

// ── Profile Selector ──────────────────────────────────────────────────────────

/** Fix (🟡): Accepts plain data parameters — no ViewModel reference needed. */
@Composable
fun ProfileSelector(
        profiles: List<MovementProfile>,
        selectedProfile: MovementProfile,
        enabled: Boolean,
        onSelect: (MovementProfile) -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "Movement Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF888888),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    FilterChip(
                            selected = profile == selectedProfile,
                            enabled = enabled,
                            onClick = { onSelect(profile) },
                            label = { Text(profile.name, maxLines = 1, fontSize = 13.sp) },
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
