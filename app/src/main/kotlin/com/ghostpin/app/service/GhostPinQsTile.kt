package com.ghostpin.app.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.ghostpin.app.data.SimulationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Quick Settings tile for toggling the GPS simulation on/off.
 *
 * When the tile is tapped:
 *  - If idle → starts simulation with the last-used config.
 *  - If running/paused → stops simulation.
 *
 * The tile state automatically syncs with [SimulationRepository.state]:
 *  - Tile.STATE_ACTIVE   → simulation is running
 *  - Tile.STATE_INACTIVE → simulation is idle or paused
 *
 * Declare in AndroidManifest with:
 *   <service android:name=".service.GhostPinQsTile"
 *            android:icon="@android:drawable/ic_menu_mylocation"
 *            android:label="GhostPin"
 *            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
 *            android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *       <meta-data android:name="android.service.quicksettings.ACTIVE_TILE"
 *                  android:value="true" />
 *   </service>
 */
@AndroidEntryPoint
class GhostPinQsTile : TileService() {

    @Inject lateinit var simulationRepository: SimulationRepository

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            simulationRepository.state.collectLatest { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        tileScope.coroutineContext.cancelChildren()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentState = simulationRepository.state.value

        if (currentState is SimulationState.Running || currentState is SimulationState.Paused) {
            // Stop simulation
            val intent = Intent(this, SimulationService::class.java).apply {
                action = SimulationService.ACTION_STOP
            }
            startService(intent)
        } else {
            // Start simulation with last-used config (or skip if none)
            val config = simulationRepository.lastUsedConfig.value ?: run {
                Toast.makeText(this, "Open GhostPin and start a simulation once to configure Quick Tile", Toast.LENGTH_LONG).show()
                openMainActivityFromTile()
                return
            }
            val intent = Intent(this, SimulationService::class.java).apply {
                action = SimulationService.ACTION_START
                putExtra(SimulationService.EXTRA_PROFILE_NAME, config.profileName)
                putExtra(SimulationService.EXTRA_START_LAT, config.startLat)
                putExtra(SimulationService.EXTRA_START_LNG, config.startLng)
                config.routeId?.let { putExtra(SimulationService.EXTRA_ROUTE_ID, it) }
            }
            startForegroundService(intent)
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun updateTileState(state: SimulationState) {
        val tile = qsTile ?: return
        when (state) {
            is SimulationState.Running -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = state.profileName
            }
            is SimulationState.Paused -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Paused"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = null
            }
        }
        tile.label = "GhostPin"
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openMainActivityFromTile() {
        val launchIntent = Intent(this, com.ghostpin.app.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            startActivity(launchIntent)
        }
    }

    companion object {
        /** Request the system to refresh the tile state. */
        fun requestUpdate(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, GhostPinQsTile::class.java),
            )
        }
    }
}
