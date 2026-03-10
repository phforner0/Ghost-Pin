package com.ghostpin.core.model

/**
 * Platform-independent mock location data.
 * Maps 1:1 to Android's Location object fields but without Android dependency.
 */
data class MockLocation(
    val lat: Double,
    val lng: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 10f,
    val verticalAccuracy: Float = 15f,
    val speedAccuracy: Float = 1f,
    val bearingAccuracy: Float = 10f,
    val elapsedRealtimeNanos: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val provider: String = "gps",
)
