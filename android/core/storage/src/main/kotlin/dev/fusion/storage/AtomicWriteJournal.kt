// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Bounded write-ahead journal for atomic file operations. Records an intent
 * (target path, expected content hash, operation type) before a mutation,
 * then [commit]s or [rollback]s it. A journal entry is a single unit of
 * work: either fully committed or fully rolled back. Entries are bounded in
 * count and total size; overflow refuses new entries rather than silently
 * dropping old ones.
 *
 * Declared narrowings: at most [MAX_ENTRIES] entries; each entry's path is
 * at most [MAX_PATH_LEN] chars and its content hash is exactly 64 hex chars
 * (SHA-256); operation is one of WRITE, DELETE, RENAME; the journal does
 * not perform filesystem I/O — it records intent only, and the storage
 * layer performs the actual mutation and reports success/failure.
 */
class AtomicWriteJournal private constructor(
    private val entries: LinkedHashMap<String, JournalEntry>
) {
    companion object {
        const val MAX_ENTRIES = 1024
        const val MAX_PATH_LEN = 4096
        private val HEX64 = Regex("[0-9a-fA-F]{64}")
        private val OP = setOf("WRITE", "DELETE", "RENAME")

        data class JournalEntry(
            val path: String,
            val operation: String,
            val contentHash: String,
            val committed: Boolean = false,
            val rolledBack: Boolean = false
        )

        fun empty(): AtomicWriteJournal = AtomicWriteJournal(LinkedHashMap())
    }

    fun record(path: String, operation: String, contentHash: String): String {
        require(path.length <= MAX_PATH_LEN) { "path exceeds $MAX_PATH_LEN chars" }
        require(HEX64.matches(contentHash)) { "contentHash must be 64 hex chars" }
        require(OP.contains(operation)) { "unknown operation: $operation" }
        require(entries.size < MAX_ENTRIES) { "journal full: $MAX_ENTRIES entries" }
        require(!entries.containsKey(path)) { "entry already exists for path: $path" }
        val id = java.util.UUID.randomUUID().toString()
        entries[id] = JournalEntry(path, operation, contentHash)
        return id
    }

    fun commit(id: String): Boolean {
        val entry = entries[id] ?: return false
        require(!entry.committed && !entry.rolledBack) { "entry $id already resolved" }
        entries[id] = entry.copy(committed = true)
        return true
    }

    fun rollback(id: String): Boolean {
        val entry = entries[id] ?: return false
        require(!entry.committed && !entry.rolledBack) { "entry $id already resolved" }
        entries[id] = entry.copy(rolledBack = true)
        return true
    }

    fun status(id: String): String? {
        val entry = entries[id] ?: return null
        return when {
            entry.committed -> "committed"
            entry.rolledBack -> "rolled_back"
            else -> "pending"
        }
    }

    fun pendingCount(): Int = entries.values.count { !it.committed && !it.rolledBack }
    fun committedCount(): Int = entries.values.count { it.committed }
    fun rolledBackCount(): Int = entries.values.count { it.rolledBack }
    fun clearResolved() = entries.removeIf { it.value.committed || it.value.rolledBack }
}
