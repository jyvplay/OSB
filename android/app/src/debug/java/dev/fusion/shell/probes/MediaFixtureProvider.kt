// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell.probes

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Debug-only real read-only descriptor provider for native decoder tests; no arbitrary paths or network. */
class MediaFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String = when (uri.path) { "/tone.wav" -> "audio/wav"; "/frames.mp4" -> "video/mp4"; else -> throw java.io.FileNotFoundException() }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r"); getType(uri)
        val name = if (uri.path == "/tone.wav") "r24c-tone.wav" else "r24c-frames.mp4"
        return ParcelFileDescriptor.open(File(requireNotNull(context).cacheDir, name), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
