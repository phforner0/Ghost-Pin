package com.ghostpin.core.security

/**
 * Sanitizes coordinates and sensitive location data from logs and exports.
 *
 * Per audit recommendations:
 * - Diagnostic logs: coords truncated to 2 decimal places (~1.1 km resolution)
 * - Crash reports: coordinates stripped entirely
 * - Export traces: coordinates can be offset by random amount
 */
object LogSanitizer {
    /**
     * Truncate coordinate to 2 decimal places (~1.1 km resolution).
     * Sufficiently anonymized for area-level debugging without revealing exact location.
     */
    fun sanitizeCoord(coord: Double): String = java.lang.String.format(java.util.Locale.US, "%.2f", coord) + "°"

    /**
     * Remove high-precision coordinates from any string.
     * Matches patterns like -23.5505, 46.63339, -123.456789
     * (any number with 4+ decimal places that looks like a coordinate).
     */
    private val COORD_REGEX = Regex("""-?\d{1,3}\.\d{4,}""")

    fun sanitizeString(s: String): String = COORD_REGEX.replace(s, "[COORD_REDACTED]")

    /**
     * Apply a random offset to coordinates for export anonymization.
     * The offset is consistent within a session (same seed = same offset).
     *
     * @param coord Original coordinate.
     * @param offsetDegrees Random offset in degrees (applied additively).
     * @return Offset coordinate.
     */
    fun offsetCoord(
        coord: Double,
        offsetDegrees: Double
    ): Double = coord + offsetDegrees
}
