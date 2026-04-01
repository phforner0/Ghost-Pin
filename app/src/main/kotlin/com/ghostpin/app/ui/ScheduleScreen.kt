package com.ghostpin.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghostpin.app.data.SimulationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    defaultConfig: SimulationConfig,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val schedules by viewModel.schedules.collectAsState()
    val exactAlarmUiState by viewModel.exactAlarmUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var startDelayMinutes by remember { mutableStateOf("5") }
    var durationMinutes by remember { mutableStateOf("15") }
    var profileExpanded by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf(viewModel.profileOptions.firstOrNull() ?: "Car") }

    LaunchedEffect(Unit) {
        viewModel.refreshExactAlarmUiState()
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshExactAlarmUiState()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agendamentos") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = startDelayMinutes,
                onValueChange = { startDelayMinutes = it.filter(Char::isDigit) },
                label = { Text("Iniciar em (min)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it.filter(Char::isDigit) },
                label = { Text("Duração (min)") },
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = profileExpanded,
                onExpandedChange = { profileExpanded = !profileExpanded },
            ) {
                OutlinedTextField(
                    value = selectedProfile,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Perfil") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false },
                ) {
                    viewModel.profileOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedProfile = option
                                profileExpanded = false
                            },
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Alarmes exatos", style = MaterialTheme.typography.titleMedium)
                    Text(exactAlarmUiState.message)
                    if (exactAlarmUiState.canOpenSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            },
                        ) {
                            Text("Abrir configurações de alarmes exatos")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.createSchedule(
                        startDelayMinutes = startDelayMinutes.toIntOrNull() ?: 5,
                        durationMinutes = durationMinutes.toIntOrNull() ?: 15,
                        config =
                            defaultConfig.copy(
                                profileName = selectedProfile,
                                profileLookupKey = selectedProfile,
                            ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Criar agendamento")
            }

            Spacer(Modifier.height(8.dp))
            Text("Agendamentos ativos")

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onCancel = { viewModel.cancelSchedule(schedule.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: com.ghostpin.app.scheduling.ScheduleEntity,
    onCancel: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ID: ${schedule.id.take(8)}")
            Text("Perfil: ${schedule.profileName}")
            Text("Início: ${formatter.format(Date(schedule.startAtMs))}")
            Text("Parada: ${schedule.stopAtMs?.let { formatter.format(Date(it)) } ?: "-"}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}
