package com.ghostpin.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.service.SimulationState
import com.ghostpin.app.ui.theme.panelBackground
import com.ghostpin.app.ui.theme.statusError
import com.ghostpin.app.ui.theme.statusSuccess
import com.ghostpin.app.ui.theme.statusWarning
import com.ghostpin.app.ui.theme.textMuted
import com.ghostpin.core.model.MockLocation

// ── Status Card ───────────────────────────────────────────────────────────────

/** Displays current simulation state with ETA when available. */
@Composable
fun SimulationStatusCard(
    state: SimulationState,
    etaText: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.panelBackground,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Simulation status",
                tint =
                    when (state) {
                        is SimulationState.Running -> MaterialTheme.colorScheme.statusSuccess
                        is SimulationState.FetchingRoute -> MaterialTheme.colorScheme.statusWarning
                        is SimulationState.Error -> MaterialTheme.colorScheme.statusError
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Show ETA when a route is loaded but simulation hasn't started
                if (state is SimulationState.Idle && etaText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "ETA: $etaText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.statusSuccess,
                    )
                }
                if (state is SimulationState.Running) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            "Volta ${state.currentLap}/${state.totalLapsLabel} · ${state.lapProgressPercent.times(
                                100
                            ).toInt()}% · " +
                                "${state.elapsedTimeSec}s elapsed" +
                                (if (etaText != null) " · ETA ~$etaText" else ""),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.textMuted,
                    )
                }
                if (state is SimulationState.Paused) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pausado na volta ${state.currentLap}/${state.totalLapsLabel} · ${state.lapProgressPercent.times(
                            100
                        ).toInt()}%",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.textMuted,
                    )
                }
                if (state is SimulationState.Error) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.statusError,
                    )
                }
            }
        }
    }
}

@Preview(name = "Status Running", showBackground = true)
@Composable
private fun SimulationStatusCardRunningPreview() {
    GhostPinTheme {
        SimulationStatusCard(
            state =
                SimulationState.Running(
                    currentLocation = MockLocation(lat = -23.5505, lng = -46.6333),
                    profileName = "Pedestrian",
                    progressPercent = 0.42f,
                    lapProgressPercent = 0.42f,
                    currentLap = 1,
                    totalLapsLabel = "3",
                    elapsedTimeSec = 120,
                    frameCount = 840,
                ),
            etaText = "3m 10s",
        )
    }
}

@Preview(name = "Status Error", showBackground = true)
@Composable
private fun SimulationStatusCardErrorPreview() {
    GhostPinTheme {
        SimulationStatusCard(
            state = SimulationState.Error("Falha ao calcular rota."),
        )
    }
}
