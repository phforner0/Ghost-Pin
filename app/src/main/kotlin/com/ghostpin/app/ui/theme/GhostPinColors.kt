package com.ghostpin.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralised color tokens for GhostPin's dark theme.
 *
 * Replaces ~90 hardcoded `Color(0xFF...)` literals scattered across UI
 * composables with named semantic tokens. This makes palette changes
 * a single-file edit and improves readability.
 */
object GhostPinColors {

    // ── Backgrounds ──────────────────────────────────────────────────────────
    val Background         = Color(0xFF121212)
    val Surface            = Color(0xFF1E1E2E)
    val SurfaceVariant     = Color(0xFF252535)
    val TopBar             = Color(0xFF1A1A2E)
    val ErrorContainer     = Color(0xFF3B1F24)

    // ── Primary / Accent ─────────────────────────────────────────────────────
    val Primary            = Color(0xFF80CBC4)  // Teal accent
    val Accent             = Color(0xFF4CAF50)  // Green for call-to-action
    val OnPrimary          = Color(0xFF121212)  // Dark text on teal
    val CardBackground     = Color(0xFF252535)  // Card surfaces

    // ── Text ─────────────────────────────────────────────────────────────────
    val TextPrimary        = Color(0xFFE0E0E0)
    val TextSecondary      = Color(0xFF888888)
    val TextTertiary       = Color(0xFF666666)
    val TextDisabled       = Color(0xFF555555)
    val TextMuted          = Color(0xFF444444)

    // ── Semantic ─────────────────────────────────────────────────────────────
    val Error              = Color(0xFFCF6679)
    val Success            = Color(0xFF4CAF50)
    val Warning            = Color(0xFFFFB300)
    val WaypointDefault    = Color(0xFF37474F)
}
