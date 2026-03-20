package com.ghostpin.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralised color tokens for GhostPin's dark theme.
 *
 * Sprint 8 — Task 32: expanded from ~17 tokens to full design system.
 * Replaces all hardcoded `Color(0xFF...)` literals across UI composables
 * with named semantic tokens. Palette changes are now a single-file edit.
 */
object GhostPinColors {

    // ── Backgrounds ──────────────────────────────────────────────────────────
    val BackgroundDeep     = Color(0xFF0A0A0A)   // Scaffold / app background
    val Background         = Color(0xFF121212)    // Default surface background
    val Surface            = Color(0xFF1E1E2E)    // Cards, panels
    val SurfaceVariant     = Color(0xFF252535)     // Elevated cards
    val PanelBackground    = Surface
    val SurfaceDim         = Color(0xFF1B1B1C)    // Waypoints panel background
    val SurfaceDark        = Color(0xFF1A1A2E)    // TopBar, GPX panel, map overlays
    val SurfaceDropdown    = Color(0xFF2A2A2B)    // Autocomplete dropdown
    val ErrorContainer     = Color(0xFF3B1A1A)    // Error surface (GPX error)
    val SuccessContainer   = Color(0xFF003734)    // Success surface (loaded route)

    // ── Primary / Accent ─────────────────────────────────────────────────────
    val Primary            = Color(0xFF80CBC4)     // Teal accent — icons, chips, links
    val PrimaryDark        = Color(0xFF00504D)     // Dark teal — container backgrounds
    val OnPrimary          = Color(0xFF003734)     // Text on primary surfaces
    val OnPrimaryDark      = Color(0xFF121212)     // Dark text on bright teal
    val Accent             = Color(0xFF00BFA5)     // Green accent — start/CTA buttons
    val AccentSuccess      = Color(0xFF4CAF50)     // Alt green — success states
    val SuccessText        = Color(0xFF00E676)     // Bright green text — confirmation

    // ── Text ─────────────────────────────────────────────────────────────────
    val TextPrimary        = Color(0xFFE0E0E0)     // Primary text
    val TextSecondary      = Color(0xFFB0BEC5)     // Secondary / subtitle text
    val TextTertiary       = Color(0xFF888888)      // Muted labels
    val TextDisabled       = Color(0xFF666666)      // Disabled / hint text
    val TextMuted          = Color(0xFF555555)      // Very muted

    // ── Semantic ─────────────────────────────────────────────────────────────
    val Error              = Color(0xFFCF6679)      // Error text / icons
    val StatusError        = Error
    val ErrorBright        = Color(0xFFEF5350)      // Delete / destructive actions
    val Warning            = Color(0xFFFFB300)      // Warning / fetching state
    val StatusWarning      = Warning
    val StatusSuccess      = Primary
    val WaypointDefault    = Color(0xFF37474F)      // Waypoint default color

    // ── Overlay ──────────────────────────────────────────────────────────────
    val MapOverlay         = Color(0xCC1A1A2E)      // Semi-transparent map overlays

    // ── Aliases (backward compat) ────────────────────────────────────────────
    val TopBar             = SurfaceDark
    val Success            = AccentSuccess
    val CardBackground     = SurfaceVariant
}
