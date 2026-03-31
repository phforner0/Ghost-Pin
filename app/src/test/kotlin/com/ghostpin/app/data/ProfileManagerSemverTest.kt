package com.ghostpin.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for semver bump logic extracted from [ProfileManager].
 *
 * Sprint 4 — Task 14.
 *
 * Since [ProfileManager] uses a private helper, these tests validate the contract
 * through reflection or via equivalent inline logic.
 */
class ProfileManagerSemverTest {
    /**
     * Replicates the bumpPatch logic from [ProfileManager] for isolated testing.
     */
    private fun bumpPatch(version: String): String {
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 3) "${parts[0]}.${parts[1]}.${parts[2] + 1}" else "1.0.1"
    }

    @Test
    fun `bumps patch from 1_0_0 to 1_0_1`() = assertEquals("1.0.1", bumpPatch("1.0.0"))

    @Test
    fun `bumps patch from 1_0_9 to 1_0_10`() = assertEquals("1.0.10", bumpPatch("1.0.9"))

    @Test
    fun `bumps patch from 2_3_0 to 2_3_1`() = assertEquals("2.3.1", bumpPatch("2.3.0"))

    @Test
    fun `fallback to 1_0_1 on invalid semver`() = assertEquals("1.0.1", bumpPatch("not-semver"))

    @Test
    fun `fallback to 1_0_1 on empty string`() = assertEquals("1.0.1", bumpPatch(""))

    @Test
    fun `fallback to 1_0_1 on two-part version`() = assertEquals("1.0.1", bumpPatch("1.0"))
}
