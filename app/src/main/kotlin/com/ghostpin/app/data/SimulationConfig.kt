package com.ghostpin.app.data

/**
 * Lightweight snapshot of the last-used simulation configuration.
 *
 * Stored in [SimulationRepository] so that quick-start surfaces
 * (QS Tile, Widget, Automation) can launch a simulation without
 * requiring the user to re-select settings.
 *
 * @property profileName Name of the [com.ghostpin.core.model.MovementProfile] (e.g. "Car").
 * @property startLat    Starting latitude.
 * @property startLng    Starting longitude.
 * @property routeId     Optional Room-persisted route ID to replay.
 */
data class SimulationConfig(
    val profileName: String,
    val startLat: Double,
    val startLng: Double,
    val routeId: String? = null,
)
