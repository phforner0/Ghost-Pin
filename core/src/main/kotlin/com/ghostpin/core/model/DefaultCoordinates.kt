package com.ghostpin.core.model

/**
 * Default map center coordinates used as initial pin positions.
 *
 * Centralised here to avoid magic numbers scattered across modules.
 * These coordinates point to downtown São Paulo, SP — the project's
 * reference city for development and testing.
 */
object DefaultCoordinates {
    const val START_LAT = -23.5505
    const val START_LNG = -46.6333
    const val END_LAT   = -23.5510
    const val END_LNG   = -46.6340
}
