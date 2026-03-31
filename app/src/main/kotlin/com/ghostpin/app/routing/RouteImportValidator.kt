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
