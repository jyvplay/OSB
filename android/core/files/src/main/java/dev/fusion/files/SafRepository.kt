package dev.fusion.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FileEntry(val uri: Uri, val name: String, val isDirectory: Boolean, val size: Long)

/** Only operates on a user-granted SAF tree. No all-files or hidden storage access. */
class SafRepository(private val context: Context) {
    fun tree(uri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, uri)

    suspend fun child(directory: DocumentFile, uri: Uri): DocumentFile? = withContext(Dispatchers.IO) {
        directory.listFiles().firstOrNull { it.uri == uri }
    }

    suspend fun list(directory: DocumentFile): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            require(directory.isDirectory) { "Not a directory" }
            directory.listFiles().map {
                FileEntry(it.uri, it.name ?: "Unnamed", it.isDirectory, it.length())
            }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    // Explicit backend denial, not merely disabled UI. Safe journaled replacements need S6.
    suspend fun createFolder(parent: DocumentFile, name: String): Result<DocumentFile> =
        FileMutationPolicy.denied(FileMutation.CREATE_DIRECTORY)
    suspend fun rename(file: DocumentFile, newName: String): Result<Unit> =
        FileMutationPolicy.denied(FileMutation.RENAME)
    suspend fun delete(file: DocumentFile): Result<Unit> =
        FileMutationPolicy.denied(FileMutation.DELETE)
    suspend fun copyTo(source: DocumentFile, parent: DocumentFile): Result<DocumentFile> =
        FileMutationPolicy.denied(FileMutation.COPY)

    suspend fun readText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open document" }
                dev.fusion.documents.TextBytes.decode(dev.fusion.documents.TextBytes.read(input))
            }
        }
    }
}
