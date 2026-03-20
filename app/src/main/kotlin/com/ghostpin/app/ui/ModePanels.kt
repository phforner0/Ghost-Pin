package com.ghostpin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.routing.GeocodingProvider
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.model.distanceMeters
import com.ghostpin.engine.interpolation.RepeatPolicy
import com.ghostpin.app.ui.theme.panelBackground
import com.ghostpin.app.ui.theme.statusError
import com.ghostpin.app.ui.theme.statusSuccess
import com.ghostpin.app.ui.theme.surfaceDim
import com.ghostpin.app.ui.theme.surfaceDropdown

// ── Mode Panels ─────────────────────────────────────────────────────────────

@Composable
private fun modeCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.panelBackground,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun modeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
)

@Composable
private fun destructiveOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.error,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
)

@Composable
fun ClassicModePanel(
        profiles: List<MovementProfile>,
        selectedProfile: MovementProfile,
        enabled: Boolean,
        onSelect: (MovementProfile) -> Unit,
        repeatPolicy: RepeatPolicy,
        repeatCount: Int,
        onRepeatPolicyChange: (RepeatPolicy) -> Unit,
        onRepeatCountChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RepeatPolicySelector(
            repeatPolicy = repeatPolicy,
            repeatCount = repeatCount,
            enabled = enabled,
            onRepeatPolicyChange = onRepeatPolicyChange,
            onRepeatCountChange = onRepeatCountChange,
        )
        ProfileSelector(
                profiles = profiles,
                selectedProfile = selectedProfile,
                enabled = enabled,
                onSelect = onSelect,
        )
    }
}

@Composable
fun JoystickModePanel() {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = modeCardColors(),
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Joystick Navigation",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
            )
            Text(
                    text =
                            "Press Start, then switch to your game. A floating joystick will let you walk in any direction in real-time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        geoSuggestions: List<GeocodingProvider.GeoResult>,
        isSearching: Boolean,
        onSearchAddress: (String) -> Unit,
        onSelectSuggestion: (GeocodingProvider.GeoResult) -> Unit,
        onClearSuggestions: () -> Unit,
        onStart: (Double) -> Unit,
        repeatPolicy: RepeatPolicy,
        repeatCount: Int,
        onRepeatPolicyChange: (RepeatPolicy) -> Unit,
        onRepeatCountChange: (Int) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val suggestionsMaxHeight = remember(screenHeight) { (screenHeight * 0.25f).coerceIn(120.dp, 240.dp) }
    val waypointsMaxHeight = remember(screenHeight) { (screenHeight * 0.30f).coerceIn(140.dp, 320.dp) }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        var addressQuery by remember { mutableStateOf("") }
        var waypointPauseSec by remember { mutableFloatStateOf(0f) }
        var showClearConfirmation by remember { mutableStateOf(false) }
        var showAllSuggestions by remember { mutableStateOf(false) }
        var showAllWaypoints by remember { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current

        Column(
                modifier = Modifier
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

            // Confirmation dialog for clearing all waypoints
            if (showClearConfirmation) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmation = false },
                    title = { Text("Clear Waypoints?") },
                    text = { Text("This will remove all ${waypoints.size} waypoints. This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearConfirmation = false
                            onClearWaypoints()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmation = false }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.panelBackground,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                        text = "Waypoints Mode",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                )
                Text(
                        text = "Long press on the map to add stops, or search by address.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                )
            }

            // Address search field with autocomplete
            Column {
                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = { value ->
                        addressQuery = value
                        onSearchAddress(value)
                    },
                    label = { Text("Search address or place") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (addressQuery.isNotBlank()) {
                            IconButton(onClick = {
                                addressQuery = ""
                                onClearSuggestions()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                )

                // Suggestions dropdown
                if (geoSuggestions.isNotEmpty()) {
                    SuggestionsList(
                        geoSuggestions = geoSuggestions,
                        maxHeight = suggestionsMaxHeight,
                        expanded = showAllSuggestions,
                        onToggleExpanded = { showAllSuggestions = !showAllSuggestions },
                        onSelectSuggestion = { result ->
                            onSelectSuggestion(result)
                            addressQuery = ""
                            onClearSuggestions()
                            showAllSuggestions = false
                        },
                    )
                }
            }

            Column {
                Text("Pause at each stop: ${"%.1f".format(waypointPauseSec)}s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Slider(
                    value = waypointPauseSec,
                    onValueChange = {
                        waypointPauseSec = it
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    enabled = enabled
                )
            }

            if (waypoints.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearConfirmation = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = destructiveOutlinedButtonColors()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear all waypoints")
                    Spacer(Modifier.width(8.dp))
                    Text("Clear all waypoints")
                }
            }

            // Waypoints List
            if (waypoints.isNotEmpty()) {
                WaypointsList(
                    waypoints = waypoints,
                    maxHeight = waypointsMaxHeight,
                    expanded = showAllWaypoints,
                    onToggleExpanded = { showAllWaypoints = !showAllWaypoints },
                    enabled = enabled,
                    onRemoveWaypoint = { index ->
                        onRemoveWaypoint(index)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            }

            ProfileSelector(
                    profiles = profiles,
                    selectedProfile = selectedProfile,
                    enabled = enabled,
                    onSelect = onSelectProfile,
            )
            RepeatPolicySelector(
                repeatPolicy = repeatPolicy,
                repeatCount = repeatCount,
                enabled = enabled,
                onRepeatPolicyChange = onRepeatPolicyChange,
                onRepeatCountChange = onRepeatCountChange,
            )
            }

            Button(
                    onClick = { onStart(waypointPauseSec.toDouble()) },
                    enabled = enabled && waypoints.size >= 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = modeButtonColors()
            ) {
                Text("Start Multi-Stop Route", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SuggestionsList(
    geoSuggestions: List<GeocodingProvider.GeoResult>,
    maxHeight: Dp,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectSuggestion: (GeocodingProvider.GeoResult) -> Unit,
) {
    val visibleItems = ((maxHeight / 56.dp).toInt()).coerceAtLeast(1)
    val visibleSuggestions = if (expanded) geoSuggestions else geoSuggestions.take(visibleItems)
    val hiddenCount = (geoSuggestions.size - visibleSuggestions.size).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDropdown, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column {
            visibleSuggestions.forEach { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSuggestion(result) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.displayName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 2,
                    )
                }
            }
            if (hiddenCount > 0) {
                TextButton(onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End)) {
                    Text("+ $hiddenCount more suggestions")
                }
            } else if (expanded && geoSuggestions.size > visibleItems) {
                TextButton(onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End)) {
                    Text("Show fewer suggestions")
                }
            }
        }
    }
}

@Composable
private fun WaypointsList(
    waypoints: List<Waypoint>,
    maxHeight: Dp,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    enabled: Boolean,
    onRemoveWaypoint: (Int) -> Unit,
) {
    val visibleItems = ((maxHeight / 48.dp).toInt()).coerceAtLeast(1)
    val visibleWaypoints = if (expanded) waypoints else waypoints.take(visibleItems)
    val hiddenCount = (waypoints.size - visibleWaypoints.size).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        visibleWaypoints.forEachIndexed { index, wp ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${wp.label ?: "${String.format("%.4f", wp.lat)}, ${String.format("%.4f", wp.lng)}"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemoveWaypoint(index) }, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (hiddenCount > 0) {
            TextButton(onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End)) {
                Text("+ $hiddenCount more waypoints")
            }
        } else if (expanded && waypoints.size > visibleItems) {
            TextButton(onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End)) {
                Text("Show fewer waypoints")
            }
        }
    }
}

@Composable
private fun RepeatPolicySelector(
    repeatPolicy: RepeatPolicy,
    repeatCount: Int,
    enabled: Boolean,
    onRepeatPolicyChange: (RepeatPolicy) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Repetição da rota", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        val policies = listOf(
            RepeatPolicy.NONE to "Sem repetição",
            RepeatPolicy.LOOP_N to "Loop N",
            RepeatPolicy.LOOP_INFINITE to "Loop ∞",
            RepeatPolicy.PING_PONG_N to "Ping-pong N",
            RepeatPolicy.PING_PONG_INFINITE to "Ping-pong ∞",
        )
        policies.forEach { (policy, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = repeatPolicy == policy,
                    onClick = { if (enabled) onRepeatPolicyChange(policy) },
                    enabled = enabled,
                )
                Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
        }
        if (repeatPolicy == RepeatPolicy.LOOP_N || repeatPolicy == RepeatPolicy.PING_PONG_N) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("N = $repeatCount", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { onRepeatCountChange((repeatCount - 1).coerceAtLeast(1)) }, enabled = enabled) { Text("-") }
                    TextButton(onClick = { onRepeatCountChange(repeatCount + 1) }, enabled = enabled) { Text("+") }
                }
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "GPX import",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                        text = "GPX Import",
                        color = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                    )
                    Button(
                            onClick = onPickFile,
                            modifier = Modifier.fillMaxWidth(),
                            colors = modeButtonColors(),
                            shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                                Icons.Default.FileOpen,
                                contentDescription = "Choose GPX file",
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
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                        )
                        Text("Parsing GPX file…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                // ── Success: show route summary + clear option ──────────────
                is SimulationViewModel.GpxLoadState.Success -> {
                    val route = gpxLoadState.route
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = route.name,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                )
                                Text(
                                        text =
                                                "${route.waypoints.size} pts · ${"%.1f".format(route.distanceMeters / 1000)} km",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = onClearRoute) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove route",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                            text = "✓ Route loaded. Press Start to begin GPX playback.",
                            color = MaterialTheme.colorScheme.statusSuccess,
                            fontSize = 12.sp,
                    )
                }

                // ── Error ──────────────────────────────────────────────────
                is SimulationViewModel.GpxLoadState.Error -> {
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "GPX error",
                                    tint = MaterialTheme.colorScheme.statusError,
                                    modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                    text = gpxLoadState.message,
                                    color = MaterialTheme.colorScheme.statusError,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    TextButton(onClick = onPickFile) {
                        Text("Try another file", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Preview(name = "Classic Active", showBackground = true)
@Composable
private fun ClassicModePanelActivePreview() {
    GhostPinTheme {
        ClassicModePanel(
            profiles = listOf(MovementProfile.PEDESTRIAN, MovementProfile.CAR),
            selectedProfile = MovementProfile.PEDESTRIAN,
            enabled = true,
            onSelect = {},
            repeatPolicy = RepeatPolicy.NONE,
            repeatCount = 2,
            onRepeatPolicyChange = {},
            onRepeatCountChange = {},
        )
    }
}

@Preview(name = "Classic Inactive", showBackground = true)
@Composable
private fun ClassicModePanelInactivePreview() {
    GhostPinTheme {
        ClassicModePanel(
            profiles = listOf(MovementProfile.PEDESTRIAN, MovementProfile.CAR),
            selectedProfile = MovementProfile.CAR,
            enabled = false,
            onSelect = {},
            repeatPolicy = RepeatPolicy.LOOP_N,
            repeatCount = 3,
            onRepeatPolicyChange = {},
            onRepeatCountChange = {},
        )
    }
}

@Preview(name = "Joystick", showBackground = true)
@Composable
private fun JoystickModePanelPreview() {
    GhostPinTheme { JoystickModePanel() }
}

@Preview(name = "Waypoints Active", showBackground = true)
@Composable
private fun WaypointsModePanelActivePreview() {
    GhostPinTheme {
        WaypointsModePanel(
            waypoints = listOf(
                Waypoint(lat = -23.5505, lng = -46.6333, label = "Start"),
                Waypoint(lat = -23.5575, lng = -46.6396, label = "Stop 2"),
            ),
            onRemoveWaypoint = {},
            onClearWaypoints = {},
            profiles = listOf(MovementProfile.PEDESTRIAN, MovementProfile.BICYCLE),
            selectedProfile = MovementProfile.PEDESTRIAN,
            enabled = true,
            onSelectProfile = {},
            geoSuggestions = emptyList(),
            isSearching = false,
            onSearchAddress = {},
            onSelectSuggestion = {},
            onClearSuggestions = {},
            onStart = {},
            repeatPolicy = RepeatPolicy.NONE,
            repeatCount = 2,
            onRepeatPolicyChange = {},
            onRepeatCountChange = {},
        )
    }
}

@Preview(name = "Waypoints Inactive", showBackground = true)
@Composable
private fun WaypointsModePanelInactivePreview() {
    GhostPinTheme {
        WaypointsModePanel(
            waypoints = listOf(Waypoint(lat = -23.5505, lng = -46.6333, label = "Only one")),
            onRemoveWaypoint = {},
            onClearWaypoints = {},
            profiles = listOf(MovementProfile.PEDESTRIAN, MovementProfile.BICYCLE),
            selectedProfile = MovementProfile.BICYCLE,
            enabled = false,
            onSelectProfile = {},
            geoSuggestions = emptyList(),
            isSearching = false,
            onSearchAddress = {},
            onSelectSuggestion = {},
            onClearSuggestions = {},
            onStart = {},
            repeatPolicy = RepeatPolicy.LOOP_N,
            repeatCount = 2,
            onRepeatPolicyChange = {},
            onRepeatCountChange = {},
        )
    }
}

@Preview(
    name = "Waypoints Compact Height",
    showBackground = true,
    device = "spec:width=360dp,height=640dp,dpi=420",
)
@Composable
private fun WaypointsModePanelCompactPreview() {
    GhostPinTheme {
        WaypointsModePanel(
            waypoints = List(12) { idx ->
                Waypoint(
                    lat = -23.5505 + (idx * 0.001),
                    lng = -46.6333 - (idx * 0.001),
                    label = "Stop ${idx + 1}",
                )
            },
            onRemoveWaypoint = {},
            onClearWaypoints = {},
            profiles = listOf(MovementProfile.PEDESTRIAN, MovementProfile.BICYCLE),
            selectedProfile = MovementProfile.PEDESTRIAN,
            enabled = true,
            onSelectProfile = {},
            geoSuggestions = List(8) { idx ->
                GeocodingProvider.GeoResult(
                    displayName = "Suggestion ${idx + 1}",
                    lat = -23.5505,
                    lng = -46.6333,
                )
            },
            isSearching = false,
            onSearchAddress = {},
            onSelectSuggestion = {},
            onClearSuggestions = {},
            onStart = {},
            repeatPolicy = RepeatPolicy.NONE,
            repeatCount = 2,
            onRepeatPolicyChange = {},
            onRepeatCountChange = {},
        )
    }
}

@Preview(
    name = "Waypoints Tall Low DPI",
    showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=280",
)
@Composable
private fun WaypointsModePanelTallPreview() {
    WaypointsModePanelCompactPreview()
}

private fun previewRoute() = Route(
    id = "preview",
    name = "Parque Ibirapuera",
    waypoints = listOf(
        Waypoint(lat = -23.5881, lng = -46.6587),
        Waypoint(lat = -23.5942, lng = -46.6512),
    ),
)

@Preview(name = "GPX Idle", showBackground = true)
@Composable
private fun GpxModePanelIdlePreview() {
    GhostPinTheme {
        GpxModePanel(
            gpxLoadState = SimulationViewModel.GpxLoadState.Idle,
            onPickFile = {},
            onClearRoute = {},
        )
    }
}

@Preview(name = "GPX Success", showBackground = true)
@Composable
private fun GpxModePanelSuccessPreview() {
    GhostPinTheme {
        GpxModePanel(
            gpxLoadState = SimulationViewModel.GpxLoadState.Success(previewRoute()),
            onPickFile = {},
            onClearRoute = {},
        )
    }
}

@Preview(name = "GPX Error", showBackground = true)
@Composable
private fun GpxModePanelErrorPreview() {
    GhostPinTheme {
        GpxModePanel(
            gpxLoadState = SimulationViewModel.GpxLoadState.Error("Arquivo inválido."),
            onPickFile = {},
            onClearRoute = {},
        )
    }
}
