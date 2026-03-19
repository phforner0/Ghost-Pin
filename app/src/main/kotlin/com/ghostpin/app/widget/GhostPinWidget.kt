package com.ghostpin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ghostpin.app.R
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState

/**
 * Home screen widget showing simulation status and a start/stop button.
 *
 * Displays:
 *  - Status label: "Simulating", "Paused", "Fetching…", or "Stopped"
 *  - Active profile name (when running or paused)
 *  - Route progress percentage (when running or paused)
 *  - Start/Stop toggle button
 *
 * Updates on simulation state changes, not per-frame, to avoid
 * excessive system resource usage.
 */
class GhostPinWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, SimulationState.Idle)
        }
    }

    companion object {
        /**
         * Update all widget instances with the current [SimulationState].
         * Called from SimulationService whenever state changes.
         */
        fun updateAll(context: Context, state: SimulationState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GhostPinWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id, state)
            }
        }


        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            state: SimulationState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.ghost_pin_widget_layout)

            // ── Status label ─────────────────────────────────────────────────
            val statusText = when (state) {
                is SimulationState.Running       -> "Simulating"
                is SimulationState.Paused        -> "Paused"
                is SimulationState.FetchingRoute -> "Fetching route…"
                is SimulationState.Error         -> "Error"
                is SimulationState.Idle          -> "Stopped"
            }
            views.setTextViewText(R.id.widget_status, statusText)

            // ── Profile + progress ────────────────────────────────────────────
            val secondaryText: String = when (state) {
                is SimulationState.Running ->
                    "${state.profileName} · ${state.progressPercent.toInt()}%"
                is SimulationState.Paused ->
                    "${state.profileName} · ${state.progressPercent.toInt()}% (paused)"
                is SimulationState.FetchingRoute ->
                    state.profileName
                else -> "—"
            }
            views.setTextViewText(R.id.widget_profile, secondaryText)

            // ── Toggle button ─────────────────────────────────────────────────────
            val isActive = state is SimulationState.Running || state is SimulationState.Paused
            views.setTextViewText(R.id.widget_button, if (isActive) "Stop" else "Start")

            // SimulationService uses ACTION_START for both "start" and "resume from pause"
            val toggleAction = if (isActive) SimulationService.ACTION_STOP else SimulationService.ACTION_START

            val toggleIntent = Intent(context, SimulationService::class.java).apply {
                action = toggleAction
            }
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            val favoriteIntent = Intent(context, SimulationService::class.java).apply {
                action = SimulationService.ACTION_START_LAST_FAVORITE
            }
            val favoritePendingIntent = PendingIntent.getForegroundService(
                context,
                1,
                favoriteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_button_favorite, favoritePendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
