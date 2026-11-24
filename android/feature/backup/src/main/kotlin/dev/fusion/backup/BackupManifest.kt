// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Bounded backup manifest. Records backup entries (path, size, checksum)
 * with an overall manifest checksum and version. Entries are bounded in
 * count and total size; paths are bounded in length; checksums must be
 * exactly 64 hex chars (SHA-256). The manifest does not perform backup
 * or restore — it records metadata for the storage layer.
 *
 * Declared narrowings: at most [MAX_ENTRIES] entries; each path at most
 * [MAX_PATH_LEN] chars; checksum exactly 64 hex chars; size non-negative
 * and at most [MAX_SIZE]; manifest version at most [MAX_VERSION_LEN] chars.
 */
class BackupManifest private constructor(
    private val version: String,
    private val entries: LinkedHashMap<String, BackupEntry>
) {
    companion object {
        const val MAX_ENTRIES = 8192
        const val MAX_PATH_LEN = 4096
        const val MAX_SIZE = 1L << 40  // 1 TiB
        const val MAX_VERSION_LEN = 32
        private val HEX64 = Regex("[0-9a-fA-F]{64}")
        private val VERSION_RE = Regex("[0-9]+(\\.[0-9]+)*")

        data class BackupEntry(
            val path: String,
            val size: Long,
            val checksum: String
        )

        data class Manifest(
            val version: String,
            val entryCount: Int,
            val totalSize: Long,
            val entries: List<BackupEntry>
        )

        fun empty(version: String): BackupManifest {
            require(VERSION_RE.matches(version)) { "invalid version: $version" }
            require(version.length <= MAX_VERSION_LEN) { "version too long" }
            return BackupManifest(version, LinkedHashMap())
        }
    }

    fun addEntry(path: String, size: Long, checksum: String) {
        require(HEX64.matches(checksum)) { "checksum must be 64 hex chars" }
        require(path.length <= MAX_PATH_LEN) { "path exceeds $MAX_PATH_LEN chars" }
        require(size >= 0 && size <= MAX_SIZE) { "size out of bounds" }
        require(entries.size < MAX_ENTRIES) { "too many entries: $MAX_ENTRIES" }
        require(!entries.containsKey(path)) { "duplicate path: $path" }
        entries[path] = BackupEntry(path, size, checksum)
    }

    fun removeEntry(path: String): Boolean = entries.remove(path) != null

    fun manifest(): Manifest {
        val totalSize = entries.values.sumOf { it.size }
        return Manifest(version, entries.size, totalSize, entries.values.toList())
    }

    fun entry(path: String): BackupEntry? = entries[path]
    fun contains(path: String): Boolean = entries.containsKey(path)
    fun size(): Int = entries.size
    fun clear() = entries.clear()
}
