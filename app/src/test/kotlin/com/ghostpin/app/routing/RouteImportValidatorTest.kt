package com.ghostpin.app.routing

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RouteImportValidatorTest {
    @Test
    fun `accepts content uri`() {
        val result = RouteImportValidator.validateUri(Uri.parse("content://com.test/routes/1"))
        assertTrue(result.isSuccess)
        assertEquals("content", result.getOrThrow().scheme)
    }

    @Test
    fun `accepts file uri`() {
        val result = RouteImportValidator.validateUri(Uri.parse("file:///storage/emulated/0/route.gpx"))
        assertTrue(result.isSuccess)
        assertEquals("file", result.getOrThrow().scheme)
    }

    @Test
    fun `rejects unsupported uri scheme`() {
        val result = RouteImportValidator.validateUri(Uri.parse("http://example.com/route.gpx"))
        assertTrue(result.isFailure)
        assertEquals(
            "Unsupported route URI scheme. Use content:// or file:// URIs.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `persist read grant only for content uri with read permission`() {
        val contentUri = Uri.parse("content://com.test/routes/1")
        val fileUri = Uri.parse("file:///storage/emulated/0/route.gpx")

        assertTrue(
            RouteImportValidator.shouldPersistReadGrant(
                contentUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        )
        assertTrue(
            !RouteImportValidator.shouldPersistReadGrant(
                contentUri,
                0,
            )
        )
        assertTrue(
            !RouteImportValidator.shouldPersistReadGrant(
                fileUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        )
    }
}
