package com.ghostpin.core.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the LogSanitizer.
 */
class LogSanitizerTest {

    @Test
    fun `sanitizeCoord truncates to 2 decimal places`() {
        assertEquals("-23.55°", LogSanitizer.sanitizeCoord(-23.550520))
        assertEquals("46.63°", LogSanitizer.sanitizeCoord(46.633390))
        assertEquals("0.00°", LogSanitizer.sanitizeCoord(0.001234))
    }

    @Test
    fun `sanitizeString removes high-precision coordinates`() {
        val input = "Location: -23.5505, -46.6333"
        val sanitized = LogSanitizer.sanitizeString(input)
        assertEquals("Location: [COORD_REDACTED], [COORD_REDACTED]", sanitized)
    }

    @Test
    fun `sanitizeString preserves non-coordinate numbers`() {
        val input = "Speed: 5.2 m/s, accuracy: 10.5m"
        val sanitized = LogSanitizer.sanitizeString(input)
        // These have fewer than 4 decimal places, so should be preserved
        assertEquals(input, sanitized)
    }

    @Test
    fun `sanitizeString handles mixed content`() {
        val input = "User at -23.55052, -46.63339 with speed 12.5"
        val sanitized = LogSanitizer.sanitizeString(input)
        assertTrue("[COORD_REDACTED]" in sanitized)
        assertTrue("12.5" in sanitized) // speed preserved
    }

    @Test
    fun `offsetCoord applies offset`() {
        val coord = -23.5505
        val offset = 0.05
        assertEquals(-23.5005, LogSanitizer.offsetCoord(coord, offset), 0.001)
    }
}
