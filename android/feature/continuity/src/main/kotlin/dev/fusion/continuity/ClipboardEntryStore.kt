// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Bounded clipboard entry store. Stores clipboard entries (content, MIME
 * type, timestamp) with per-entry size limits and a total entry cap.
 * Entries expire based on a maximum age. Does not interact with the
 * system clipboard — the continuity layer supplies the data.
 *
 * Declared narrowings: at most [MAX_ENTRIES] entries; entry IDs at most
 * [MAX_ID_LEN] chars; content at most [MAX_CONTENT_BYTES]; MIME types at
 * most [MAX_MIME_LEN] chars; timestamps non-negative and at most [MAX_TS];
 * max age non-negative and at most [MAX_AGE_MS].
 */
class ClipboardEntryStore private constructor(
    private val entries: LinkedHashMap<String, ClipboardEntry>
) {
    companion object {
        const val MAX_ENTRIES = 256
        const val MAX_ID_LEN = 128
        const val MAX_CONTENT_BYTES = 1 << 20 // 1 MiB
        const val MAX_MIME_LEN = 128
        const val MAX_TS = Long.MAX_VALUE
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")
        private val MIME_RE = Regex("[a-z]+/[a-z0-9.+-]+")

        data class ClipboardEntry(
            val content: ByteArray,
            val mimeType: String,
            val timestampMs: Long
        )

        fun empty(): ClipboardEntryStore = ClipboardEntryStore(LinkedHashMap())
    }

    fun addEntry(entryId: String, content: ByteArray, mimeType: String, timestampMs: Long): Boolean {
        require(ID_RE.matches(entryId)) { "invalid entry id: $entryId" }
        require(entryId.length <= MAX_ID_LEN) { "entry id too long" }
        require(content.size <= MAX_CONTENT_BYTES) { "content too large" }
        require(MIME_RE.matches(mimeType)) { "invalid MIME type: $mimeType" }
        require(mimeType.length <= MAX_MIME_LEN) { "MIME type too long" }
        require(timestampMs >= 0 && timestampMs <= MAX_TS) { "timestamp out of bounds" }
        require(entries.size < MAX_ENTRIES || entries.containsKey(entryId)) {
            "too many entries: $MAX_ENTRIES"
        }

        // Enforce max age: reject entries older than MAX_AGE_MS from now
        val now = System.currentTimeMillis()
        if (now - timestampMs > MAX_AGE_MS) {
            return false
        }

        entries[entryId] = ClipboardEntry(content.copyOf(), mimeType, timestampMs)
        return true
    }

    fun removeEntry(entryId: String): Boolean = entries.remove(entryId) != null

    fun entry(entryId: String): ClipboardEntry? = entries[entryId]
    fun entryCount(): Int = entries.size
    fun expiredCount(nowMs: Long): Int {
        require(nowMs >= 0) { "negative nowMs" }
        return entries.values.count { nowMs - it.timestampMs > MAX_AGE_MS }
    }
    fun clear() = entries.clear()
}
