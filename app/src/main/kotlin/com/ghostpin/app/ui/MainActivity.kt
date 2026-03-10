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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/** Single-activity entry point for GhostPin. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: SimulationViewModel by viewModels()

    // Fix (🟡): The permission result callback was a no-op `{ /* no-op */ }`.
    // Now it triggers a Snackbar message when the user denies permissions,
    // preventing silent failure where the app would appear broken with no feedback.
    // The Snackbar is coordinated via a channel-style StateFlow read by GhostPinScreen.
    private val _permissionMessage = mutableStateOf<String?>(null)

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.entries
                .filter { !it.value }
                .map { it.key.substringAfterLast('.') }

            if (denied.isNotEmpty()) {
                _permissionMessage.value =
                    "Location permission required for GPS simulation. " +
                    "Please grant it in app settings."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        requestPermissions()
        setContent {
            GhostPinTheme {
                GhostPinScreen(
                    viewModel          = viewModel,
                    permissionMessage  = _permissionMessage.value,
                    onPermissionMessageDismissed = { _permissionMessage.value = null },
                    onStartSimulation  = ::startSimulation,
                    onStopSimulation   = ::stopSimulation,
                )
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).also { list ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) locationPermissionLauncher.launch(needed.toTypedArray())
    }

    private fun startSimulation(profile: MovementProfile) {
        val intent = Intent(this, SimulationService::class.java).apply {
            putExtra(SimulationService.EXTRA_PROFILE_NAME,  profile.name)
            putExtra(SimulationService.EXTRA_START_LAT,     viewModel.startLat.value)
            putExtra(SimulationService.EXTRA_START_LNG,     viewModel.startLng.value)
            putExtra(SimulationService.EXTRA_END_LAT,       viewModel.endLat.value)
            putExtra(SimulationService.EXTRA_END_LNG,       viewModel.endLng.value)
            putExtra(SimulationService.EXTRA_FREQUENCY_HZ,  SimulationService.DEFAULT_FREQUENCY)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSimulation() {
        startService(Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_STOP
        })
    }
}

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun GhostPinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = Color(0xFF80CBC4),
            onPrimary        = Color(0xFF003734),
            primaryContainer = Color(0xFF00504D),
            secondary        = Color(0xFFB0BEC5),
            surface          = Color(0xFF121212),
            onSurface        = Color(0xFFE0E0E0),
            background       = Color(0xFF0A0A0A),
            onBackground     = Color(0xFFE0E0E0),
            error            = Color(0xFFCF6679),
        ),
        content = content,
    )
}

// ── Main Screen ──────────────────────────────────────────────────────────────

/**
 * Root composable that collects all ViewModels StateFlows and distributes
 * them as plain values to child composables.
 *
 * Fix (🟡): Child composables (InteractiveMap, SimulationStatusCard, ProfileSelector)
 * now receive data + callbacks instead of the ViewModel directly.
 * This makes them independently previewable, testable, and reusable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostPinScreen(
    viewModel:                   SimulationViewModel,
    permissionMessage:           String?,
    onPermissionMessageDismissed: () -> Unit,
    onStartSimulation:           (MovementProfile) -> Unit,
    onStopSimulation:            () -> Unit,
) {
    // Collect all state at this level and pass primitives down
    val selectedProfile  by viewModel.selectedProfile.collectAsState()
    val simulationState  by viewModel.simulationState.collectAsState()
    // Fix (🟡): isBusy now comes from ViewModel, not computed here
    val isBusy           by viewModel.isBusy.collectAsState()
    val startLat         by viewModel.startLat.collectAsState()
    val startLng         by viewModel.startLng.collectAsState()
    val endLat           by viewModel.endLat.collectAsState()
    val endLng           by viewModel.endLng.collectAsState()
    val route            by viewModel.route.collectAsState()
    val startPlaced      by viewModel.startPlaced.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope    = rememberCoroutineScope()

    // Show Snackbar when permissions are denied
    LaunchedEffect(permissionMessage) {
        if (permissionMessage != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message     = permissionMessage,
                    actionLabel = "Dismiss",
                    duration    = SnackbarDuration.Long,
                )
                onPermissionMessageDismissed()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("GhostPin", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = Color.Transparent,
                    titleContentColor = Color(0xFF80CBC4),
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { if (isBusy) onStopSimulation() else onStartSimulation(selectedProfile) },
                containerColor = if (isBusy) Color(0xFFCF6679) else Color(0xFF80CBC4),
                contentColor   = Color(0xFF003734),
                icon = {
                    Icon(
                        imageVector = if (isBusy) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                },
                text = { Text(if (isBusy) "Stop" else "Start", fontWeight = FontWeight.Bold) },
            )
        },
        containerColor = Color(0xFF0A0A0A),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Map with overlays — state hoisted; no viewModel reference inside
            InteractiveMap(
                startLat        = startLat,
                startLng        = startLng,
                endLat          = endLat,
                endLng          = endLng,
                route           = route,
                startPlaced     = startPlaced,
                simulationState = simulationState,
                onMapLongPress  = viewModel::onMapLongPress,
                modifier        = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            SimulationStatusCard(state = simulationState)

            ProfileSelector(
                profiles        = viewModel.profiles,
                selectedProfile = selectedProfile,
                enabled         = !isBusy,
                onSelect        = viewModel::selectProfile,
            )

            Spacer(Modifier.height(72.dp))  // FAB clearance
        }
    }
}

// ── Map composable ────────────────────────────────────────────────────────────

/**
 * Renders the MapLibre map with route, pins and overlay indicators.
 *
 * Fix (🟡): Accepts plain data parameters instead of the ViewModel reference.
 * Fix (🔴): AnimatedVisibility is now correctly wrapped in [Column] to provide
 *   the [ColumnScope] required by this extension function. The `?.let { }` lambda
 *   is replaced by `if (x != null) { }` which maintains the @Composable context.
 */
@Composable
fun InteractiveMap(
    startLat:        Double,
    startLng:        Double,
    endLat:          Double,
    endLng:          Double,
    route:           Route?,
    startPlaced:     Boolean,
    simulationState: SimulationState,
    onMapLongPress:  (Double, Double) -> Unit,
    modifier:        Modifier = Modifier,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    var mapController by remember { mutableStateOf<MapController?>(null) }

    // Manage MapView lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
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

    // Update map whenever relevant state changes
    LaunchedEffect(simulationState, startLat, startLng, endLat, endLng, route) {
        val controller = mapController ?: return@LaunchedEffect
        when (simulationState) {
            is SimulationState.Idle, is SimulationState.FetchingRoute -> {
                controller.clearPosition()
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
            }
            is SimulationState.Running -> {
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
                controller.updatePosition(simulationState.currentLocation)
            }
            is SimulationState.Paused  -> controller.updatePosition(simulationState.lastLocation)
            is SimulationState.Error   -> controller.clearPosition()
        }
    }

    // Hint text: tells user what the next long-press will do
    val hintText: String? = when {
        simulationState is SimulationState.Running       -> null
        simulationState is SimulationState.FetchingRoute -> null
        !startPlaced                                     -> "Long-press to set Start"
        else                                             -> "Long-press to set End"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView.apply {
                        getMapAsync { mapLibreMap ->
                            mapController = MapController(mapLibreMap, context) { latLng ->
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
            //   "@Composable invocations can only happen from the context of a @Composable function"
            // Replaced with `if (hintText != null) { }` which preserves @Composable context.

            // Hint overlay (bottom-center)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                AnimatedVisibility(
                    visible = hintText != null,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    if (hintText != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC1A1A2E),
                        ) {
                            Text(
                                text       = hintText,
                                color      = Color(0xFF80CBC4),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Loading overlay (top-end) — shown while OSRM fetches the route
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                AnimatedVisibility(
                    visible = simulationState is SimulationState.FetchingRoute,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC1A1A2E),
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                color       = Color(0xFF80CBC4),
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

/**
 * Fix (🟡): Accepts [SimulationState] directly — no ViewModel reference needed.
 */
@Composable
fun SimulationStatusCard(state: SimulationState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.MyLocation,
                contentDescription = null,
                tint = when (state) {
                    is SimulationState.Running       -> Color(0xFF80CBC4)
                    is SimulationState.FetchingRoute -> Color(0xFFFFB300)
                    is SimulationState.Error         -> Color(0xFFCF6679)
                    else                             -> Color(0xFF666666)
                },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = when (state) {
                        is SimulationState.Idle          -> "Idle"
                        is SimulationState.FetchingRoute -> "Fetching route… • ${state.profileName}"
                        is SimulationState.Running       -> "Simulating • ${state.profileName}"
                        is SimulationState.Paused        -> "Paused • ${state.profileName}"
                        is SimulationState.Error         -> "Error"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = Color(0xFFE0E0E0),
                )
                if (state is SimulationState.Running) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = "Frame #${state.frameCount} · " +
                                   "${state.progressPercent.toInt()}% · " +
                                   "${state.elapsedTimeSec}s",
                        fontSize = 13.sp,
                        color    = Color(0xFF888888),
                    )
                }
                if (state is SimulationState.Error) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = state.message,
                        fontSize = 12.sp,
                        color    = Color(0xFFCF6679),
                    )
                }
            }
        }
    }
}

// ── Profile Selector ──────────────────────────────────────────────────────────

/**
 * Fix (🟡): Accepts plain data parameters — no ViewModel reference needed.
 */
@Composable
fun ProfileSelector(
    profiles:        List<MovementProfile>,
    selectedProfile: MovementProfile,
    enabled:         Boolean,
    onSelect:        (MovementProfile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Movement Profile",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = Color(0xFF888888),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    FilterChip(
                        selected = profile == selectedProfile,
                        enabled  = enabled,
                        onClick  = { onSelect(profile) },
                        label    = { Text(profile.name, maxLines = 1, fontSize = 13.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF80CBC4),
                            selectedLabelColor     = Color(0xFF121212),
                        ),
                    )
                }
            }
        }
    }
}
