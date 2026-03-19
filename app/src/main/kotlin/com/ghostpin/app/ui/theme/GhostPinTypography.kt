package com.ghostpin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * GhostPin typography scale.
 *
 * Sprint 8 — Task 32: consistent type scale based on Material 3 defaults
 * with GhostPin-specific overrides for weight and size.
 */
val GhostPinTypography = Typography(
    // App title (TopAppBar)
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    // Section titles (panel headers)
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    // Status labels, chip labels
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    // Body text (descriptions, instructions)
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    // Small body / captions
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    // Chip labels, waypoint indices
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    // Tiny labels (progress %, ETA)
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
)
