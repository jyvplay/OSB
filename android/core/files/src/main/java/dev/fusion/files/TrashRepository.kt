package dev.fusion.files

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

/** Legacy receipt inspection only. All mutation entry points fail closed until S6.
 * Existing blobs/receipts are retained. Receipt metadata is not a new verification
 * of the old deletion or of the durability/integrity of a legacy trash blob.
 */
class TrashRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "trash")
    private val index: File get() = File(dir, "index.json")

    companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
    }

    private fun readIndex(): MutableList<TrashEntry> {
        if (!index.exists()) return mutableListOf()
        require(index.length() <= 2L * 1024 * 1024) { "Legacy trash index is oversized; retained unchanged" }
        val text=index.inputStream().use { dev.fusion.documents.TextBytes.decode(dev.fusion.documents.TextBytes.read(it)) }
        return run {
            val array = JSONArray(text)
            require(array.length() <= 1024) { "Legacy trash index has too many receipts; retained unchanged" }
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                val id=o.getString("id")
                require(UUID.fromString(id).toString()==id) { "Invalid legacy trash identity" }
                require(o.getLong("size") in 0..MAX_BYTES)
                TrashEntry(
                    id = id,
                    name = o.getString("name"),
                    mime = o.optString("mime", "application/octet-stream"),
                    size = o.optLong("size"),
                    deletedAt = o.optLong("deletedAt"),
                    sourceRemoved = o.optBoolean("sourceRemoved", false)
                )
            }
        }
    }

    suspend fun list(): List<TrashEntry> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.deletedAt }
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        readIndex().sumOf { it.size }
    }

    suspend fun moveToTrash(source: DocumentFile): Result<TrashEntry> = FileMutationPolicy.denied(FileMutation.TRASH)
    suspend fun restore(entryId: String, destination: DocumentFile): Result<String> = FileMutationPolicy.denied(FileMutation.RESTORE)
    suspend fun purge(entryId: String): Result<Unit> = FileMutationPolicy.denied(FileMutation.PURGE)
    suspend fun purgeAll(): Result<Int> = FileMutationPolicy.denied(FileMutation.PURGE_ALL)
}
