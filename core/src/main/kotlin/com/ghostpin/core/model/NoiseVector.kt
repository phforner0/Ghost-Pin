package com.ghostpin.core.model

/**
 * Noise displacement vector produced by the noise model pipeline.
 * [isJump] flags multipath heavy-tail events for downstream accuracy adjustment.
 */
data class NoiseVector(
    val lat: Double,
    val lng: Double,
    val isJump: Boolean = false,
)
