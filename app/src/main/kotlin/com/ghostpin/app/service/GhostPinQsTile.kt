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
import com.ghostpin.app.service.GhostPinQsTileClickDecision.OpenApp
import com.ghostpin.app.service.GhostPinQsTileClickDecision.StartFavorite
import com.ghostpin.app.service.GhostPinQsTileClickDecision.StopActiveSimulation
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

        if (shouldStopCurrentSimulation(currentState)) {
            startService(buildStopIntent(this))
        } else {
            tileScope.launch {
                when (val decision = resolveClickDecision(simulationRepository.applyMostRecentFavorite())) {
                    is StopActiveSimulation -> startService(buildStopIntent(this@GhostPinQsTile))
                    is StartFavorite -> {
                        val intent = SimulationService.createStartIntent(this@GhostPinQsTile, decision.config)
                        startForegroundService(intent)
                    }
                    is OpenApp -> {
                        Toast
                            .makeText(
                                this@GhostPinQsTile,
                                decision.reason,
                                Toast.LENGTH_LONG,
                            ).show()
                        openMainActivityFromTile()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun updateTileState(state: SimulationState) {
        val tile = qsTile ?: return
        val model = renderTileModel(state, Build.VERSION.SDK_INT)
        tile.state = model.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = model.subtitle
        }
        tile.label = model.label
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openMainActivityFromTile() {
        val launchIntent =
            Intent(this, com.ghostpin.app.ui.MainActivity::class.java)
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

internal data class GhostPinQsTileModel(
    val state: Int,
    val subtitle: String?,
    val label: String = "GhostPin",
)

internal sealed interface GhostPinQsTileClickDecision {
    data object StopActiveSimulation : GhostPinQsTileClickDecision

    data class StartFavorite(
        val config: com.ghostpin.app.data.SimulationConfig,
    ) : GhostPinQsTileClickDecision

    data class OpenApp(
        val reason: String,
    ) : GhostPinQsTileClickDecision
}

internal fun renderTileModel(
    state: SimulationState,
    sdkInt: Int,
): GhostPinQsTileModel =
    when (state) {
        is SimulationState.Running ->
            GhostPinQsTileModel(
                state = Tile.STATE_ACTIVE,
                subtitle = if (sdkInt >= Build.VERSION_CODES.Q) state.profileName else null,
            )

        is SimulationState.Paused ->
            GhostPinQsTileModel(
                state = Tile.STATE_ACTIVE,
                subtitle = if (sdkInt >= Build.VERSION_CODES.Q) "Paused" else null,
            )

        else ->
            GhostPinQsTileModel(
                state = Tile.STATE_INACTIVE,
                subtitle = null,
            )
    }

internal fun shouldStopCurrentSimulation(state: SimulationState): Boolean =
    state is SimulationState.Running || state is SimulationState.Paused

internal fun resolveClickDecision(resolution: SimulationRepository.FavoriteResolution,): GhostPinQsTileClickDecision =
    when (resolution) {
        is SimulationRepository.FavoriteResolution.Valid -> StartFavorite(resolution.config)
        is SimulationRepository.FavoriteResolution.Invalid -> OpenApp(resolution.reason)
    }

internal fun buildStopIntent(context: android.content.Context): Intent =
    Intent(context, SimulationService::class.java).apply {
        action = SimulationService.ACTION_STOP
    }
