package com.ghostpin.app.ui

import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.core.model.AppMode
import kotlin.math.abs

internal fun scheduleRouteSummary(
    routeId: String?,
    appMode: AppMode,
    waypointsCount: Int,
): String =
    when {
        routeId != null -> "Rota salva"
        appMode == AppMode.GPX && waypointsCount >= 2 -> "Rota importada ($waypointsCount pts)"
        appMode == AppMode.WAYPOINTS && waypointsCount >= 2 -> "$waypointsCount waypoints"
        appMode == AppMode.JOYSTICK -> "Sem rota fixa"
        waypointsCount >= 2 -> "$waypointsCount pontos"
        else -> "Pinos diretos"
    }

internal fun SimulationConfig.routeSummary(): String =
    scheduleRouteSummary(routeId = routeId, appMode = appMode, waypointsCount = waypoints.size)

internal fun ScheduleEntity.modeDisplayName(): String = resolvedAppMode().displayName

internal fun ScheduleEntity.routeSummary(): String =
    scheduleRouteSummary(
        routeId = routeId,
        appMode = resolvedAppMode(),
        waypointsCount = SimulationConfig.deserializeWaypoints(waypointsJson).size,
    )

internal fun formatRelativeScheduleTime(
    targetMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): String {
    val diffMs = targetMs - nowMs
    if (abs(diffMs) < 60_000L) return "agora"

    val totalMinutes = abs(diffMs) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText =
        buildString {
            if (hours > 0) {
                append("${hours}h")
            }
            if (minutes > 0 || hours == 0L) {
                if (isNotEmpty()) append(' ')
                append("${minutes}min")
            }
        }

    return if (diffMs > 0) {
        "em $durationText"
    } else {
        "há $durationText"
    }
}

internal fun ScheduleEntity.windowSummary(nowMs: Long = System.currentTimeMillis()): String =
    when {
        startAtMs > nowMs -> "Inicia ${formatRelativeScheduleTime(startAtMs, nowMs)}"
        stopAtMs != null && stopAtMs > nowMs -> "Em andamento · termina ${formatRelativeScheduleTime(stopAtMs, nowMs)}"
        stopAtMs != null -> "Janela encerrada ${formatRelativeScheduleTime(stopAtMs, nowMs)}"
        else -> "Iniciado ${formatRelativeScheduleTime(startAtMs, nowMs)}"
    }

private fun ScheduleEntity.resolvedAppMode(): AppMode {
    return runCatching { AppMode.valueOf(appMode) }.getOrDefault(AppMode.CLASSIC)
}
