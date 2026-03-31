package com.ghostpin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghostpin.app.data.db.SimulationHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onReplay: (SimulationHistoryEntity) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de simulações") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                },
                actions = {
                    TextButton(onClick = viewModel::clearHistory) { Text("Limpar") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error != null) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }

            if (state.items.isEmpty() && state.isLoading) {
                CircularProgressIndicator()
            } else if (state.items.isEmpty()) {
                Text("Nenhuma simulação registrada ainda.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        HistoryItemCard(item = item, onReplay = { onReplay(item) })
                    }
                }
            }

            if (state.hasMore) {
                OutlinedButton(
                    onClick = viewModel::loadNextPage,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Carregar mais")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: SimulationHistoryEntity,
    onReplay: () -> Unit,
) {
    Card {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.profileName, style = MaterialTheme.typography.titleMedium)
            Text("Início: ${item.startedAtMs.asDateTime()}")
            Text("Status: ${item.resultStatus}")
            Text("Distância: ${"%.1f".format(Locale.US, item.distanceMeters)} m")
            Text("Duração: ${item.durationMs?.let(::formatDurationMs) ?: "-"}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onReplay) {
                    Text("Repetir simulação")
                }
            }
        }
    }
}

private fun Long.asDateTime(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return formatter.format(Date(this))
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}m ${sec}s"
}
