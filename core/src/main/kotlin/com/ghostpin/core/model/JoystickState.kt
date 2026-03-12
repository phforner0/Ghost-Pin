package com.ghostpin.core.model

/**
 * Represents the current thumb position of the virtual joystick.
 *
 * Emitted by JoystickView (UI tier) and consumed by SimulationService (engine tier)
 * to allow direct manual control over the simulated GPS location.
 */
data class JoystickState(
    /** Bearing in degrees: 0 = north, 90 = east, 180 = south, 270 = west. */
    val angle: Float = 0f,
    /** 0.0 = centred (stopped), 1.0 = full deflection (max speed). */
    val magnitude: Float = 0f,
)
