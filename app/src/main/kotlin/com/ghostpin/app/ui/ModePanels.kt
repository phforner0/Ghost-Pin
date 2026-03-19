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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.routing.GeocodingProvider
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.model.distanceMeters

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
        geoSuggestions: List<GeocodingProvider.GeoResult>,
        isSearching: Boolean,
        onSearchAddress: (String) -> Unit,
        onSelectSuggestion: (GeocodingProvider.GeoResult) -> Unit,
        onClearSuggestions: () -> Unit,
        onStart: (Double) -> Unit,
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
            var addressQuery by remember { mutableStateOf("") }
            var waypointPauseSec by remember { mutableFloatStateOf(0f) }
            var showClearConfirmation by remember { mutableStateOf(false) }
            val haptic = LocalHapticFeedback.current

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
                            Text("Clear All", color = Color(0xFFEF5350))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmation = false }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = Color(0xFF1E1E2E),
                    titleContentColor = Color(0xFFE0E0E0),
                    textContentColor = Color(0xFFB0BEC5),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                        text = "Waypoints Mode",
                        color = Color(0xFF80CBC4),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                )
                Text(
                        text = "Long press on the map to add stops, or search by address.",
                        color = Color(0xFFB0BEC5),
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
                                color = Color(0xFF80CBC4)
                            )
                        } else if (addressQuery.isNotBlank()) {
                            IconButton(onClick = {
                                addressQuery = ""
                                onClearSuggestions()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFB0BEC5))
                            }
                        }
                    }
                )

                // Suggestions dropdown
                if (geoSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2B))
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            geoSuggestions.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectSuggestion(result)
                                            addressQuery = ""
                                            onClearSuggestions()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF80CBC4),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = result.displayName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column {
                Text("Pause at each stop: ${"%.1f".format(waypointPauseSec)}s", color = Color(0xFFB0BEC5), fontSize = 12.sp)
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear all waypoints")
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
                                text = "${index + 1}. ${wp.label ?: "${String.format("%.4f", wp.lat)}, ${String.format("%.4f", wp.lng)}"}",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                onRemoveWaypoint(index)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }, enabled = enabled) {
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
                    onClick = { onStart(waypointPauseSec.toDouble()) },
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
                        contentDescription = "GPX import",
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
                                    contentDescription = "GPX error",
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
