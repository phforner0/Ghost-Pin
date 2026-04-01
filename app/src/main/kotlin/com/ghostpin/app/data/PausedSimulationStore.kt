package com.ghostpin.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MockLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pausedSimulationDataStore: DataStore<Preferences> by preferencesDataStore(name = "paused_simulation")

data class PausedSimulationSnapshot(
    val config: SimulationConfig,
    val pausedState: SimulationState.Paused,
    val activeHistoryId: String?,
    val activeHistoryStartedAtMs: Long?,
)

interface PausedSimulationStore {
    suspend fun readSnapshot(): PausedSimulationSnapshot?

    suspend fun writeSnapshot(snapshot: PausedSimulationSnapshot?)
}

object NoOpPausedSimulationStore : PausedSimulationStore {
    override suspend fun readSnapshot(): PausedSimulationSnapshot? = null

    override suspend fun writeSnapshot(snapshot: PausedSimulationSnapshot?) = Unit
}

@Singleton
class DataStorePausedSimulationStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PausedSimulationStore {
        companion object {
            private val KEY_PAUSED_SNAPSHOT = stringPreferencesKey("paused_snapshot_json")
        }

        override suspend fun readSnapshot(): PausedSimulationSnapshot? {
            val json = context.pausedSimulationDataStore.data.first()[KEY_PAUSED_SNAPSHOT] ?: return null
            return runCatching { deserializePausedSnapshot(json) }.getOrNull()
        }

        override suspend fun writeSnapshot(snapshot: PausedSimulationSnapshot?) {
            context.pausedSimulationDataStore.edit { prefs ->
                if (snapshot == null) {
                    prefs.remove(KEY_PAUSED_SNAPSHOT)
                } else {
                    prefs[KEY_PAUSED_SNAPSHOT] = serializePausedSnapshot(snapshot)
                }
            }
        }
    }

internal fun serializePausedSnapshot(snapshot: PausedSimulationSnapshot): String =
    JSONObject()
        .apply {
            put("config", serializeSimulationConfig(snapshot.config))
            put("activeHistoryId", snapshot.activeHistoryId ?: JSONObject.NULL)
            put("activeHistoryStartedAtMs", snapshot.activeHistoryStartedAtMs ?: JSONObject.NULL)
            put(
                "pausedState",
                JSONObject()
                    .apply {
                        put("lat", snapshot.pausedState.lastLocation.lat)
                        put("lng", snapshot.pausedState.lastLocation.lng)
                        put("altitude", snapshot.pausedState.lastLocation.altitude)
                        put(
                            "bearing",
                            snapshot.pausedState.lastLocation.bearing
                                .toDouble()
                        )
                        put(
                            "speed",
                            snapshot.pausedState.lastLocation.speed
                                .toDouble()
                        )
                        put(
                            "accuracy",
                            snapshot.pausedState.lastLocation.accuracy
                                .toDouble()
                        )
                        put("timestampMs", snapshot.pausedState.lastLocation.timestampMs)
                        put("elapsedRealtimeNanos", snapshot.pausedState.lastLocation.elapsedRealtimeNanos)
                        put("profileName", snapshot.pausedState.profileName)
                        put("progressPercent", snapshot.pausedState.progressPercent.toDouble())
                        put("lapProgressPercent", snapshot.pausedState.lapProgressPercent.toDouble())
                        put("currentLap", snapshot.pausedState.currentLap)
                        put("totalLapsLabel", snapshot.pausedState.totalLapsLabel)
                        put("direction", snapshot.pausedState.direction)
                        put("elapsedTimeSec", snapshot.pausedState.elapsedTimeSec)
                    }.toString(),
            )
        }.toString()

internal fun deserializePausedSnapshot(json: String): PausedSimulationSnapshot {
    val root = JSONObject(json)
    val paused = JSONObject(root.getString("pausedState"))
    return PausedSimulationSnapshot(
        config = deserializeSimulationConfig(root.getString("config")),
        activeHistoryId = if (root.isNull("activeHistoryId")) null else root.getString("activeHistoryId"),
        activeHistoryStartedAtMs = if (root.isNull("activeHistoryStartedAtMs")) null else root.getLong("activeHistoryStartedAtMs"),
        pausedState =
            SimulationState.Paused(
                lastLocation =
                    MockLocation(
                        lat = paused.getDouble("lat"),
                        lng = paused.getDouble("lng"),
                        altitude = paused.optDouble("altitude", 0.0),
                        bearing = paused.optDouble("bearing", 0.0).toFloat(),
                        speed = paused.optDouble("speed", 0.0).toFloat(),
                        accuracy = paused.optDouble("accuracy", 5.0).toFloat(),
                        timestampMs = paused.optLong("timestampMs", 0L),
                        elapsedRealtimeNanos = paused.optLong("elapsedRealtimeNanos", 0L),
                    ),
                profileName = paused.optString("profileName"),
                progressPercent = paused.optDouble("progressPercent", 0.0).toFloat(),
                lapProgressPercent = paused.optDouble("lapProgressPercent", 0.0).toFloat(),
                currentLap = paused.optInt("currentLap", 1),
                totalLapsLabel = paused.optString("totalLapsLabel", "1"),
                direction = paused.optInt("direction", 1),
                elapsedTimeSec = paused.optLong("elapsedTimeSec", 0L),
            ),
    )
}
