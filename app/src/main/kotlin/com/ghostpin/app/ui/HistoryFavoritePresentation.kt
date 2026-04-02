package com.ghostpin.app.ui

import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.core.model.AppMode
import java.util.Locale

internal fun SimulationConfig.favoriteNameSuggestion(): String =
    when (val routeSummary = routeSummary()) {
        "Pinos diretos" -> "$profileName · ${appMode.displayName}"
        else -> "$profileName · $routeSummary"
    }

internal fun FavoriteSimulationEntity.modeDisplayName(): String = runCatching { AppMode.valueOf(appMode).displayName }.getOrElse { appMode }

internal fun FavoriteSimulationEntity.routeSummary(): String = toSimulationConfig().routeSummary()

internal fun FavoriteSimulationEntity.summaryLine(): String = "${modeDisplayName()} · ${routeSummary()}"

internal fun SimulationHistoryEntity.modeDisplayName(): String = runCatching { AppMode.valueOf(appMode).displayName }.getOrElse { appMode }

internal fun SimulationHistoryEntity.routeSummary(): String = toSimulationConfig().routeSummary()

internal fun SimulationHistoryEntity.summaryLine(): String = "${modeDisplayName()} · ${routeSummary()}"

internal fun SimulationHistoryEntity.statusLabel(): String =
    when (resultStatus) {
        "RUNNING" -> "Em andamento"
        "COMPLETED" -> "Concluída"
        "INTERRUPTED" -> "Interrompida"
        "ERROR" -> "Falhou"
        else -> resultStatus
    }

internal fun SimulationHistoryEntity.avgSpeedLabel(): String? = avgSpeedMs?.let { "${"%.1f".format(Locale.US, it)} m/s" }
