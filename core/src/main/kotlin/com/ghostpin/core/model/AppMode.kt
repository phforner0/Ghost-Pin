package com.ghostpin.core.model

/**
 * Defines the main operating modes of the GhostPin application (Sprint 6).
 *
 * - [CLASSIC]: standard start/end pin point-to-point routing
 * - [JOYSTICK]: live navigation driven by an on-screen joystick overlay
 * - [WAYPOINTS]: multi-stop routing allowing the user to plot an exact path manually
 * - [GPX]: playback of pre-recorded GPX trace files
 */
enum class AppMode(val displayName: String) {
    CLASSIC("Classic"),
    JOYSTICK("Joystick"),
    WAYPOINTS("Waypoints"),
    GPX("GPX Import")
}
