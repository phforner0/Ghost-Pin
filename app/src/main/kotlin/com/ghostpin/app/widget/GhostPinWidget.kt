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

/**
 * Home screen widget showing simulation status and a start/stop button.
 *
 * Displays:
 *  - Status label ("Simulating" / "Paused" / "Stopped")
 *  - Active profile name (when running)
 *  - Start/Stop toggle button
 *
 * Updates only on simulation state changes, not per-frame, to avoid
 * excessive system resource usage.
 *
 * Declare in AndroidManifest with:
 *   <receiver android:name=".widget.GhostPinWidget"
 *             android:exported="false">
 *       <intent-filter>
 *           <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.appwidget.provider"
 *           android:resource="@xml/ghost_pin_widget_info" />
 *   </receiver>
 */
class GhostPinWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, isRunning = false, profileName = null)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.ghostpin.widget.TOGGLE"

        /**
         * Update all widget instances with the current simulation state.
         * Called from SimulationService when state changes.
         */
        fun updateAll(context: Context, isRunning: Boolean, profileName: String?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GhostPinWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id, isRunning, profileName)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            isRunning: Boolean,
            profileName: String?,
        ) {
            val views = RemoteViews(context.packageName, R.layout.ghost_pin_widget_layout)

            // Status text
            views.setTextViewText(
                R.id.widget_status,
                if (isRunning) "Simulating" else "Stopped",
            )

            // Profile name
            views.setTextViewText(
                R.id.widget_profile,
                profileName ?: "—",
            )

            // Button text and action
            views.setTextViewText(
                R.id.widget_button,
                if (isRunning) "Stop" else "Start",
            )

            // PendingIntent for the toggle button
            val toggleIntent = Intent(context, SimulationService::class.java).apply {
                action = if (isRunning) SimulationService.ACTION_STOP else SimulationService.ACTION_START
            }
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
