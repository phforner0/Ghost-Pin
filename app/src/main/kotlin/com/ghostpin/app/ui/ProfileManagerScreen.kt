package com.ghostpin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.core.model.MovementProfile

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    onBack: () -> Unit,
    onUseProfile: (MovementProfile) -> Unit,
    onUseAndAnalyze: (MovementProfile) -> Unit = {},
    viewModel: ProfileManagerViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorState by remember { mutableStateOf<ProfileEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfileEntity?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }

    var pendingMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar perfis") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showPresetDialog = true
                        },
                        modifier = Modifier.testTag("add_profile_button"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Novo perfil")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onUse = { onUseProfile(profile.toDomain()) },
                    onUseAndAnalyze = { onUseAndAnalyze(profile.toDomain()) },
                    onClone = {
                        val suggestedName = nextAvailableProfileName("${profile.name} Copy", profiles)
                        editorState = ProfileEditorState.create(profile.toDomain().copy(name = suggestedName))
                    },
                    onEdit = {
                        editorState = ProfileEditorState.edit(profile.id, profile.toDomain())
                    },
                    onDelete = {
                        deleteTarget = profile
                    },
                )
            }
        }
    }

    editorState?.let { state ->
        ProfileEditorDialog(
            initialState = state,
            existingProfiles = profiles,
            onDismiss = { editorState = null },
            onSave = { updatedState ->
                val profile =
                    updatedState.toMovementProfile() ?: run {
                        pendingMessage = "Revise os campos numéricos do perfil."
                        return@ProfileEditorDialog
                    }
                if (updatedState.profileId == null) {
                    viewModel.create(
                        profile = profile,
                        onResult = {
                            editorState = null
                            pendingMessage = "Perfil salvo: ${profile.name}"
                        },
                        onError = { pendingMessage = it },
                    )
                } else {
                    viewModel.update(
                        id = updatedState.profileId,
                        updated = profile,
                        onError = { pendingMessage = it },
                    )
                    editorState = null
                    pendingMessage = "Perfil atualizado: ${profile.name}"
                }
            },
        )
    }

    if (showPresetDialog) {
        PresetPickerDialog(
            onDismiss = { showPresetDialog = false },
            onSelectPreset = { preset ->
                showPresetDialog = false
                val suggestedName = nextAvailableProfileName("${preset.name} Copy", profiles)
                editorState = ProfileEditorState.create(preset.copy(name = suggestedName))
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir perfil") },
            text = { Text("Excluir o perfil customizado '${profile.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(profile.id) { pendingMessage = it }
                        deleteTarget = null
                    }
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    onUse: () -> Unit,
    onUseAndAnalyze: () -> Unit,
    onClone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val heuristics = remember(profile) { profileHeuristics(profile.toDomain()) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (profile.isBuiltIn) "Built-in" else "Custom") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }

            Text("Versão ${profile.version}")
            Text("Velocidade máx: ${"%.1f".format(profile.maxSpeedMs)} m/s")
            Text("Aceleração máx: ${"%.1f".format(profile.maxAccelMs2)} m/s²")
            Text("Turn rate máx: ${"%.1f".format(profile.maxTurnRateDegPerSec)} deg/s")

            if (heuristics.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    heuristics.forEach { label ->
                        AssistChip(onClick = {}, enabled = false, label = { Text(label) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUse) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.padding(2.dp))
                    Text("Usar")
                }
                if (BuildConfig.REALISM_DIAGNOSTICS_ENABLED) {
                    TextButton(onClick = onUseAndAnalyze, modifier = Modifier.testTag("use_analyze_${profile.id}")) {
                        Icon(Icons.Default.Science, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("Usar e analisar")
                    }
                }
                TextButton(onClick = onClone) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(2.dp))
                    Text("Clonar")
                }
                if (!profile.isBuiltIn) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("Editar")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("Excluir")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    initialState: ProfileEditorState,
    existingProfiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (ProfileEditorState) -> Unit,
) {
    var state by rememberSaveable(initialState.profileId, stateSaver = ProfileEditorState.Saver) {
        mutableStateOf(initialState)
    }
    val validation = remember(state, existingProfiles) { ProfileValidation.from(state, existingProfiles) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.profileId == null) "Novo perfil" else "Editar perfil") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeader("Identidade", "Nome e assinatura visual do perfil")
                ProfileField(
                    label = "Nome",
                    value = state.name,
                    supportingText = "O nome precisa ser único e ajuda a identificar o perfil na simulação.",
                    isError = validation.nameError != null,
                    errorText = validation.nameError,
                    testTag = "profile_name_field",
                ) { state = state.copy(name = it) }

                SectionHeader("Ruído e multipath", "Parâmetros de oscilação e reflexões do sinal")
                ProfileField("Theta", state.theta, supportingText = "Taxa de retorno ao centro do processo OU.") {
                    state =
                        state.copy(theta = it)
                }
                ProfileField("Sigma", state.sigma, supportingText = "Intensidade base do ruído do sinal.") {
                    state =
                        state.copy(sigma = it)
                }
                ProfileField("pMultipath", state.pMultipath, supportingText = "Probabilidade de reflexões por frame.") {
                    state =
                        state.copy(pMultipath = it)
                }
                ProfileField(
                    "Laplace scale",
                    state.laplaceScale,
                    supportingText = "Amplitude dos desvios de multipath."
                ) {
                    state =
                        state.copy(laplaceScale = it)
                }

                SectionHeader("Cinética", "Limites físicos de velocidade, aceleração e curva")
                ProfileField(
                    "Velocidade máx (m/s)",
                    state.maxSpeedMs,
                    supportingText = "Use valores condizentes com o tipo de veículo."
                ) {
                    state =
                        state.copy(maxSpeedMs = it)
                }
                ProfileField(
                    "Aceleração máx (m/s²)",
                    state.maxAccelMs2,
                    supportingText = "Controla suavidade e responsividade."
                ) {
                    state =
                        state.copy(maxAccelMs2 = it)
                }
                ProfileField(
                    "Turn rate máx (deg/s)",
                    state.maxTurnRateDegPerSec,
                    supportingText = "Limite de curva por segundo."
                ) {
                    state =
                        state.copy(maxTurnRateDegPerSec = it)
                }

                SectionHeader("Perda de sinal", "Comportamento de túnel e recuperação")
                ProfileField(
                    "Prob. túnel/s",
                    state.tunnelProbabilityPerSec,
                    supportingText = "Chance de entrar em perda de sinal."
                ) {
                    state =
                        state.copy(tunnelProbabilityPerSec = it)
                }
                ProfileField(
                    "Duração túnel média",
                    state.tunnelDurationMeanSec,
                    supportingText = "Duração esperada do bloqueio."
                ) {
                    state =
                        state.copy(tunnelDurationMeanSec = it)
                }
                ProfileField(
                    "Sigma túnel",
                    state.tunnelDurationSigmaSec,
                    supportingText = "Variação da duração do bloqueio."
                ) {
                    state =
                        state.copy(tunnelDurationSigmaSec = it)
                }

                SectionHeader("Saída", "Altitude e quantização final do sinal")
                ProfileField("Sigma altitude", state.altitudeSigma, supportingText = "Ruído aplicado à altitude.") {
                    state =
                        state.copy(altitudeSigma = it)
                }
                ProfileField(
                    "Quantização decimal",
                    state.quantizationDecimals,
                    integer = true,
                    supportingText = "Casas decimais preservadas no mock location.",
                ) {
                    state = state.copy(quantizationDecimals = it)
                }

                if (!validation.isValid) {
                    Text(
                        text = validation.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("profile_validation_message"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (validation.isValid) onSave(state) },
                enabled = validation.isValid,
                modifier = Modifier.testTag("profile_save_button"),
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun PresetPickerDialog(
    onDismiss: () -> Unit,
    onSelectPreset: (MovementProfile) -> Unit,
) {
    val presets =
        listOf(
            "A partir de Pedestrian" to MovementProfile.PEDESTRIAN,
            "A partir de Car" to MovementProfile.CAR,
            "A partir de Bicycle" to MovementProfile.BICYCLE,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, preset) ->
                    Button(
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    integer: Boolean = false,
    supportingText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    testTag: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        isError = isError,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            ),
        supportingText = {
            when {
                errorText != null -> Text(errorText, color = MaterialTheme.colorScheme.error)
                supportingText != null -> Text(supportingText)
            }
        },
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ProfileValidation(
    val isValid: Boolean,
    val message: String,
    val nameError: String? = null,
) {
    companion object {
        fun from(
            state: ProfileEditorState,
            existingProfiles: List<ProfileEntity>,
        ): ProfileValidation {
            val trimmedName = state.name.trim()
            if (trimmedName.isBlank()) {
                return ProfileValidation(false, "Informe um nome para o perfil.", nameError = "Nome obrigatório")
            }

            val duplicate =
                existingProfiles.firstOrNull {
                    it.id != state.profileId && it.name.equals(trimmedName, ignoreCase = true)
                }
            if (duplicate != null) {
                return ProfileValidation(false, "Já existe um perfil com esse nome.", nameError = "Nome já em uso")
            }

            val numericValues =
                listOf(
                    state.theta to "Theta",
                    state.sigma to "Sigma",
                    state.pMultipath to "pMultipath",
                    state.laplaceScale to "Laplace scale",
                    state.maxSpeedMs to "Velocidade máxima",
                    state.maxAccelMs2 to "Aceleração máxima",
                    state.maxTurnRateDegPerSec to "Turn rate máxima",
                    state.tunnelProbabilityPerSec to "Probabilidade de túnel",
                    state.tunnelDurationMeanSec to "Duração média de túnel",
                    state.tunnelDurationSigmaSec to "Sigma de túnel",
                    state.altitudeSigma to "Sigma de altitude",
                )
            val invalidNumeric = numericValues.firstOrNull { it.first.toDoubleOrNull() == null }
            if (invalidNumeric != null) {
                return ProfileValidation(false, "Revise o campo '${invalidNumeric.second}'.")
            }

            val quantization = state.quantizationDecimals.toIntOrNull()
            if (quantization == null) {
                return ProfileValidation(false, "Quantização decimal precisa ser um inteiro.")
            }
            if (quantization !in 0..8) {
                return ProfileValidation(false, "Quantização decimal deve ficar entre 0 e 8.")
            }

            if ((state.maxSpeedMs.toDoubleOrNull() ?: 0.0) <= 0.0) {
                return ProfileValidation(false, "Velocidade máxima deve ser maior que zero.")
            }

            if ((state.maxAccelMs2.toDoubleOrNull() ?: 0.0) <= 0.0) {
                return ProfileValidation(false, "Aceleração máxima deve ser maior que zero.")
            }

            return ProfileValidation(true, "")
        }
    }
}

private fun nextAvailableProfileName(
    baseName: String,
    profiles: List<ProfileEntity>,
): String {
    val trimmedBase = baseName.trim()
    if (profiles.none { it.name.equals(trimmedBase, ignoreCase = true) }) return trimmedBase

    var index = 2
    while (true) {
        val candidate = "$trimmedBase $index"
        if (profiles.none { it.name.equals(candidate, ignoreCase = true) }) return candidate
        index++
    }
}

private data class ProfileEditorState(
    val profileId: String?,
    val name: String,
    val theta: String,
    val sigma: String,
    val pMultipath: String,
    val laplaceScale: String,
    val maxSpeedMs: String,
    val maxAccelMs2: String,
    val maxTurnRateDegPerSec: String,
    val tunnelProbabilityPerSec: String,
    val tunnelDurationMeanSec: String,
    val tunnelDurationSigmaSec: String,
    val altitudeSigma: String,
    val quantizationDecimals: String,
) {
    fun toMovementProfile(): MovementProfile? {
        val thetaValue = theta.toDoubleOrNull() ?: return null
        val sigmaValue = sigma.toDoubleOrNull() ?: return null
        val pMultipathValue = pMultipath.toDoubleOrNull() ?: return null
        val laplaceScaleValue = laplaceScale.toDoubleOrNull() ?: return null
        val maxSpeedValue = maxSpeedMs.toDoubleOrNull() ?: return null
        val maxAccelValue = maxAccelMs2.toDoubleOrNull() ?: return null
        val maxTurnRateValue = maxTurnRateDegPerSec.toDoubleOrNull() ?: return null
        val tunnelProbabilityValue = tunnelProbabilityPerSec.toDoubleOrNull() ?: return null
        val tunnelMeanValue = tunnelDurationMeanSec.toDoubleOrNull() ?: return null
        val tunnelSigmaValue = tunnelDurationSigmaSec.toDoubleOrNull() ?: return null
        val altitudeSigmaValue = altitudeSigma.toDoubleOrNull() ?: return null
        val quantizationValue = quantizationDecimals.toIntOrNull() ?: return null

        return MovementProfile(
            name = name.trim().ifBlank { "Custom Profile" },
            theta = thetaValue,
            sigma = sigmaValue,
            pMultipath = pMultipathValue,
            laplaceScale = laplaceScaleValue,
            maxSpeedMs = maxSpeedValue,
            maxAccelMs2 = maxAccelValue,
            maxTurnRateDegPerSec = maxTurnRateValue,
            tunnelProbabilityPerSec = tunnelProbabilityValue,
            tunnelDurationMeanSec = tunnelMeanValue,
            tunnelDurationSigmaSec = tunnelSigmaValue,
            altitudeSigma = altitudeSigmaValue,
            quantizationDecimals = quantizationValue,
        )
    }

    companion object {
        fun create(profile: MovementProfile): ProfileEditorState = fromProfile(null, profile)

        fun edit(
            profileId: String,
            profile: MovementProfile
        ): ProfileEditorState = fromProfile(profileId, profile)

        private fun fromProfile(
            profileId: String?,
            profile: MovementProfile
        ): ProfileEditorState =
            ProfileEditorState(
                profileId = profileId,
                name = profile.name,
                theta = profile.theta.toString(),
                sigma = profile.sigma.toString(),
                pMultipath = profile.pMultipath.toString(),
                laplaceScale = profile.laplaceScale.toString(),
                maxSpeedMs = profile.maxSpeedMs.toString(),
                maxAccelMs2 = profile.maxAccelMs2.toString(),
                maxTurnRateDegPerSec = profile.maxTurnRateDegPerSec.toString(),
                tunnelProbabilityPerSec = profile.tunnelProbabilityPerSec.toString(),
                tunnelDurationMeanSec = profile.tunnelDurationMeanSec.toString(),
                tunnelDurationSigmaSec = profile.tunnelDurationSigmaSec.toString(),
                altitudeSigma = profile.altitudeSigma.toString(),
                quantizationDecimals = profile.quantizationDecimals.toString(),
            )

        val Saver =
            androidx.compose.runtime.saveable.listSaver<ProfileEditorState, String>(
                save = {
                    listOf(
                        it.profileId.orEmpty(),
                        it.name,
                        it.theta,
                        it.sigma,
                        it.pMultipath,
                        it.laplaceScale,
                        it.maxSpeedMs,
                        it.maxAccelMs2,
                        it.maxTurnRateDegPerSec,
                        it.tunnelProbabilityPerSec,
                        it.tunnelDurationMeanSec,
                        it.tunnelDurationSigmaSec,
                        it.altitudeSigma,
                        it.quantizationDecimals,
                    )
                },
                restore = {
                    ProfileEditorState(
                        profileId = it[0].ifBlank { null },
                        name = it[1],
                        theta = it[2],
                        sigma = it[3],
                        pMultipath = it[4],
                        laplaceScale = it[5],
                        maxSpeedMs = it[6],
                        maxAccelMs2 = it[7],
                        maxTurnRateDegPerSec = it[8],
                        tunnelProbabilityPerSec = it[9],
                        tunnelDurationMeanSec = it[10],
                        tunnelDurationSigmaSec = it[11],
                        altitudeSigma = it[12],
                        quantizationDecimals = it[13],
                    )
                },
            )
    }
}
