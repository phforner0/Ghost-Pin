package com.ghostpin.app.routing

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Nominatim geocoding JSON parsing.
 *
 * Sprint 9 — Task 34.
 *
 * Tests the JSON parsing logic in isolation via a local helper that mirrors
 * [GeocodingProvider.parseResults] without needing an Android context.
 */
class GeocodingProviderTest {

    /**
     * Local reimplementation of the Nominatim parse logic for testing.
     * Mirrors [GeocodingProvider.parseResults] exactly.
     */
    private data class GeoResult(val displayName: String, val lat: Double, val lng: Double)

    private fun parseResults(json: String): List<GeoResult> {
        val array = JSONArray(json)
        val results = mutableListOf<GeoResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val lat = obj.optString("lat").toDoubleOrNull() ?: continue
            val lon = obj.optString("lon").toDoubleOrNull() ?: continue
            val displayName = obj.optString("display_name", "Unknown")
            results.add(GeoResult(displayName, lat, lon))
        }
        return results
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `parses valid Nominatim response`() {
        val json = """
            [
              {"lat": "-23.5505199", "lon": "-46.6333094", "display_name": "São Paulo, SP, Brazil"},
              {"lat": "-22.9068467", "lon": "-43.1728965", "display_name": "Rio de Janeiro, RJ, Brazil"}
            ]
        """.trimIndent()

        val results = parseResults(json)
        assertEquals(2, results.size)
        assertEquals("São Paulo, SP, Brazil", results[0].displayName)
        assertEquals(-23.5505199, results[0].lat, 1e-6)
        assertEquals(-46.6333094, results[0].lng, 1e-6)
        assertEquals("Rio de Janeiro, RJ, Brazil", results[1].displayName)
    }

    @Test
    fun `returns empty list for empty JSON array`() {
        val results = parseResults("[]")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `skips entries with missing lat`() {
        val json = """
            [
              {"lon": "-46.6333", "display_name": "Missing lat"},
              {"lat": "-22.9", "lon": "-43.1", "display_name": "Valid"}
            ]
        """.trimIndent()

        val results = parseResults(json)
        assertEquals(1, results.size)
        assertEquals("Valid", results[0].displayName)
    }

    @Test
    fun `skips entries with non-numeric coordinates`() {
        val json = """[{"lat": "not-a-number", "lon": "-46.6333", "display_name": "Bad lat"}]"""
        val results = parseResults(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `uses Unknown for missing display_name`() {
        val json = """[{"lat": "-23.5", "lon": "-46.6"}]"""
        val results = parseResults(json)
        assertEquals(1, results.size)
        assertEquals("Unknown", results[0].displayName)
    }

    @Test
    fun `handles many results`() {
        val entries = (1..20).joinToString(",") { i ->
            """{"lat": "-23.$i", "lon": "-46.$i", "display_name": "Location $i"}"""
        }
        val results = parseResults("[$entries]")
        assertEquals(20, results.size)
    }
}
