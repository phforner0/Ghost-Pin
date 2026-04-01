package com.ghostpin.app.routing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

internal object RouteImportValidator {
    private val allowedSchemes = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)

    fun validateUri(uri: Uri?): Result<Uri> {
        if (uri == null) {
            return Result.failure(IllegalArgumentException("Missing route URI."))
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme.isNullOrBlank() || scheme !in allowedSchemes || uri.isOpaque) {
            return Result.failure(
                IllegalArgumentException("Unsupported route URI scheme. Use content:// or file:// URIs.")
            )
        }

        return Result.success(uri)
    }

    fun shouldPersistReadGrant(
        uri: Uri,
        intentFlags: Int,
    ): Boolean {
        val hasReadGrant = (intentFlags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        return uri.scheme == ContentResolver.SCHEME_CONTENT && hasReadGrant
    }

    fun persistReadGrantIfNeeded(
        contentResolver: ContentResolver,
        uri: Uri,
        intentFlags: Int,
    ): Result<Boolean> {
        if (!shouldPersistReadGrant(uri, intentFlags)) return Result.success(false)

        val persistableFlags =
            intentFlags and (
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

        return runCatching {
            contentResolver.takePersistableUriPermission(uri, persistableFlags)
            true
        }
    }

    fun resolveDisplayName(
        contentResolver: ContentResolver,
        uri: Uri
    ): String? =
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } else {
            uri.lastPathSegment?.substringAfterLast('/')
        }
}
