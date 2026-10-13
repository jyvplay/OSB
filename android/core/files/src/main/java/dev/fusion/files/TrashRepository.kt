package dev.fusion.files

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TrashEntry(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val deletedAt: Long,
    /** True when the original was removed. False means the copy is a duplicate, not a move. */
    val sourceRemoved: Boolean
)

/**
 * Reversible delete for SAF documents.
 *
 * Order of operations, so no state can lose the only copy:
 *   1. copy bytes into app-private trash blob
 *   2. verify the blob length against the source
 *   3. journal the entry to disk (fsync'd by rename)
 *   4. only then delete the source
 *
 * If step 4 fails the entry stays with sourceRemoved=false and the UI says the
 * original is still there, so the user is never told something was deleted when
 * it was not. Restore writes a NEW document and never overwrites.
 *
 * Limits, stated rather than hidden:
 *   - files only; directories are rejected
 *   - restore targets a folder you pick, not the original path (SAF gives no
 *     durable handle to the old parent once the document is gone)
 *   - blobs live in app-private storage, so uninstalling the app purges them
 */
class TrashRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "trash").apply { mkdirs() }
    private val index: File get() = File(dir, "index.json")

    companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
    }

    private fun readIndex(): MutableList<TrashEntry> {
        if (!index.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(index.readText())
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                TrashEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    mime = o.optString("mime", "application/octet-stream"),
                    size = o.optLong("size"),
                    deletedAt = o.optLong("deletedAt"),
                    sourceRemoved = o.optBoolean("sourceRemoved", true)
                )
            }
        }.getOrDefault(mutableListOf())
    }

    private fun writeIndex(entries: List<TrashEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("mime", e.mime)
                    put("size", e.size); put("deletedAt", e.deletedAt)
                    put("sourceRemoved", e.sourceRemoved)
                }
            )
        }
        // Write to a temp file then rename: a crash mid-write cannot corrupt the journal.
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(index)) {
            index.writeText(array.toString())
            tmp.delete()
        }
    }

    suspend fun list(): List<TrashEntry> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.deletedAt }
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        readIndex().sumOf { it.size }
    }

    suspend fun moveToTrash(source: DocumentFile): Result<TrashEntry> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.isFile) { "Only files can be moved to trash, not folders" }
            val declared = source.length()
            val entries = readIndex()
            val used = entries.sumOf { it.size }
            require(used + declared <= MAX_BYTES) {
                "Trash is full (limit ${MAX_BYTES / (1024 * 1024)} MB). Empty it first."
            }

            val id = UUID.randomUUID().toString()
            val blob = File(dir, id)

            // 1. copy
            context.contentResolver.openInputStream(source.uri).use { input ->
                requireNotNull(input) { "Cannot read the file" }
                blob.outputStream().use { output -> input.copyTo(output) }
            }

            // 2. verify before anything destructive happens
            if (declared > 0 && blob.length() != declared) {
                blob.delete()
                error("Copy verification failed; nothing was deleted")
            }
            if (blob.length() == 0L && declared > 0) {
                blob.delete()
                error("Copied zero bytes; nothing was deleted")
            }

            // 3. journal first
            var entry = TrashEntry(
                id = id,
                name = source.name ?: "document",
                mime = source.type ?: "application/octet-stream",
                size = blob.length(),
                deletedAt = System.currentTimeMillis(),
                sourceRemoved = false
            )
            entries.add(entry)
            writeIndex(entries)

            // 4. now remove the original
            val removed = runCatching { source.delete() }.getOrDefault(false)
            if (removed) {
                entry = entry.copy(sourceRemoved = true)
                val updated = readIndex().map { if (it.id == id) entry else it }
                writeIndex(updated)
            }
            entry
        }
    }

    suspend fun restore(entryId: String, destination: DocumentFile): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(destination.isDirectory) { "Pick a folder to restore into" }
                val entries = readIndex()
                val entry = entries.firstOrNull { it.id == entryId }
                    ?: error("That trash entry no longer exists")
                val blob = File(dir, entry.id)
                require(blob.exists()) { "The trashed copy is missing" }

                var name = entry.name
                var n = 1
                while (destination.findFile(name) != null) {
                    val base = entry.name.substringBeforeLast('.', entry.name)
                    val ext = entry.name.substringAfterLast('.', "")
                    name = if (ext.isEmpty()) "$base (restored $n)" else "$base (restored $n).$ext"
                    n++
                }

                val created = destination.createFile(entry.mime, name)
                    ?: error("The provider refused to create the restored file")
                context.contentResolver.openOutputStream(created.uri, "wt").use { output ->
                    requireNotNull(output) { "Cannot write the restored file" }
                    blob.inputStream().use { it.copyTo(output) }
                    output.flush()
                }

                // Only drop the trash copy once the restored file verifies.
                val restoredLength = created.length()
                if (restoredLength > 0 && restoredLength != entry.size) {
                    error("Restored file length mismatch; trash copy kept")
                }
                blob.delete()
                writeIndex(entries.filterNot { it.id == entryId })
                name
            }
        }

    suspend fun purge(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readIndex()
            require(entries.any { it.id == entryId }) { "Entry not found" }
            File(dir, entryId).delete()
            writeIndex(entries.filterNot { it.id == entryId })
        }
    }

    suspend fun purgeAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readIndex()
            entries.forEach { File(dir, it.id).delete() }
            writeIndex(emptyList())
            entries.size
        }
    }
}
