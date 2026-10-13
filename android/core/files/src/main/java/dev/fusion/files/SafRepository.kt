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

    suspend fun createFolder(parent: DocumentFile, name: String): Result<DocumentFile> = withContext(Dispatchers.IO) {
        runCatching {
            require(name.isNotBlank() && '/' !in name) { "Enter a valid folder name" }
            require(parent.findFile(name) == null) { "A file with that name already exists" }
            parent.createDirectory(name) ?: error("Provider rejected folder creation")
        }
    }

    suspend fun rename(file: DocumentFile, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(newName.isNotBlank() && '/' !in newName) { "Enter a valid name" }
            check(file.renameTo(newName)) { "Provider does not support rename" }
        }
    }

    suspend fun delete(file: DocumentFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { check(file.delete()) { "Provider rejected delete" } }
    }

    /** Creates a *new* document; never overwrites source. No atomicity claimed for SAF providers. */
    suspend fun copyTo(source: DocumentFile, parent: DocumentFile): Result<DocumentFile> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.isFile) { "Copying directories is not supported yet" }
            val base = source.name ?: "document"
            var name = base
            var suffix = 1
            while (parent.findFile(name) != null) name = "copy-$suffix-$base".also { suffix++ }
            val dest = parent.createFile(source.type ?: "application/octet-stream", name)
                ?: error("Provider rejected document creation")
            try {
                context.contentResolver.openInputStream(source.uri).use { input ->
                    requireNotNull(input) { "Cannot read source" }
                    context.contentResolver.openOutputStream(dest.uri, "w").use { output ->
                        requireNotNull(output) { "Cannot write destination" }
                        input.copyTo(output)
                        output.flush()
                    }
                }
                // SAF length can be unknown. When both sizes are known, reject mismatch.
                if (source.length() > 0 && dest.length() > 0) {
                    check(source.length() == dest.length()) { "Copy verification failed" }
                }
                dest
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
        }
    }

    suspend fun readText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open document" }
                // Text editor is intentionally capped until large-file streaming is implemented.
                val bytes = input.readNBytes(2 * 1024 * 1024 + 1)
                require(bytes.size <= 2 * 1024 * 1024) { "Text files above 2 MB are not supported yet" }
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    /** ACTION_CREATE_DOCUMENT has already created a new destination; never use this on a source URI. */
    suspend fun writeNewText(uri: Uri, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt").use { output ->
                requireNotNull(output) { "Cannot open destination" }
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }
}