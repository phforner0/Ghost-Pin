package com.ghostpin.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ghostpin.core.model.AppMode
import com.ghostpin.engine.interpolation.RepeatPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.simulationConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "simulation_config")

interface SimulationConfigStore {
    suspend fun readLastUsedConfig(): SimulationConfig?

    suspend fun writeLastUsedConfig(config: SimulationConfig?)
}

object NoOpSimulationConfigStore : SimulationConfigStore {
    override suspend fun readLastUsedConfig(): SimulationConfig? = null

    override suspend fun writeLastUsedConfig(config: SimulationConfig?) = Unit
}

@Singleton
class DataStoreSimulationConfigStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SimulationConfigStore {
        companion object {
            private val KEY_LAST_USED_CONFIG = stringPreferencesKey("last_used_config_json")
        }

        override suspend fun readLastUsedConfig(): SimulationConfig? {
            val json = context.simulationConfigDataStore.data.first()[KEY_LAST_USED_CONFIG] ?: return null
            return runCatching { deserializeSimulationConfig(json) }.getOrNull()
        }

        override suspend fun writeLastUsedConfig(config: SimulationConfig?) {
            context.simulationConfigDataStore.edit { prefs ->
                if (config == null) {
                    prefs.remove(KEY_LAST_USED_CONFIG)
                } else {
                    prefs[KEY_LAST_USED_CONFIG] = serializeSimulationConfig(config)
                }
            }
        }
    }

internal fun serializeSimulationConfig(config: SimulationConfig): String =
    JSONObject()
        .apply {
            put("profileName", config.profileName)
            put("profileLookupKey", config.profileLookupKey)
            put("startLat", config.startLat)
            put("startLng", config.startLng)
            put("endLat", config.endLat)
            put("endLng", config.endLng)
            put("routeId", config.routeId ?: JSONObject.NULL)
            put("appMode", config.appMode.name)
            put("waypoints", config.serializedWaypoints())
            put("waypointPauseSec", config.waypointPauseSec)
            put("speedRatio", config.speedRatio)
            put("frequencyHz", config.frequencyHz)
            put("repeatPolicy", config.repeatPolicy.name)
            put("repeatCount", config.repeatCount)
        }.toString()

internal fun deserializeSimulationConfig(json: String): SimulationConfig {
    val root = JSONObject(json)
    return SimulationConfig(
        profileName = root.getString("profileName"),
        profileLookupKey = root.optString("profileLookupKey", root.getString("profileName")),
        startLat = root.getDouble("startLat"),
        startLng = root.getDouble("startLng"),
        endLat = root.optDouble("endLat"),
        endLng = root.optDouble("endLng"),
        routeId = if (root.isNull("routeId")) null else root.getString("routeId"),
        appMode = AppMode.valueOf(root.optString("appMode", AppMode.CLASSIC.name)),
        waypoints = SimulationConfig.deserializeWaypoints(root.optString("waypoints", "[]")),
        waypointPauseSec = root.optDouble("waypointPauseSec", 0.0),
        speedRatio = root.optDouble("speedRatio", 1.0),
        frequencyHz = root.optInt("frequencyHz", 5),
        repeatPolicy = RepeatPolicy.valueOf(root.optString("repeatPolicy", RepeatPolicy.NONE.name)),
        repeatCount = root.optInt("repeatCount", 1),
    )
}
