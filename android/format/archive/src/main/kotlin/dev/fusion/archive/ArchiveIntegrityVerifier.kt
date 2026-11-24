package dev.fusion.archive

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Bounded archive integrity verifier.
 *
 * Computes and verifies CRC-32 checksums for every entry in a ZIP archive,
 * detecting corruption, truncation, and tampering. Does not extract files.
 *
 * Bounds (per call):
 *   - entries <= 10,000
 *   - total uncompressed bytes <= 2 GiB
 *   - entry name length <= 4,096 UTF-16 code units
 *
 * Returns a per-entry integrity report. Throws IllegalArgumentException for
 * bounds violations or corrupt archives.
 */
object ArchiveIntegrityVerifier {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_NAME_LENGTH = 4_096

    data class EntryReport(
        val name: String,
        val crc32: Long,
        val size: Long,
        val valid: Boolean,
        val error: String? = null
    )

    data class ArchiveReport(
        val valid: Boolean,
        val entries: List<EntryReport>,
        val totalEntries: Int,
        val totalBytes: Long
    )

    /**
     * Verify the integrity of every entry in a ZIP archive by computing
     * CRC-32 over the uncompressed bytes and comparing with the stored value.
     */
    fun verify(zip: File): ArchiveReport {
        require(zip.exists()) { "archive does not exist: ${zip.absolutePath}" }
        require(zip.isFile) { "not a file: ${zip.absolutePath}" }

        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            require(entries.size <= MAX_ENTRIES) {
                "too many entries: ${entries.size} > $MAX_ENTRIES"
            }

            val reports = mutableListOf<EntryReport>()
            var totalBytes = 0L
            var allValid = true

            for (entry in entries) {
                require(entry.name.length <= MAX_NAME_LENGTH) {
                    "entry name too long: ${entry.name.length} > $MAX_NAME_LENGTH"
                }

                val crc = CRC32()
                var size = 0L
                var entryValid = true
                var error: String? = null

                try {
                    archive.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (read > -1) {
                            read = input.read(buffer)
                            if (read > 0) {
                                crc.update(buffer, 0, read)
                                size += read.toLong()
                                require(totalBytes + size <= MAX_TOTAL_BYTES) {
                                    "archive expands to more than 2 GiB"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    entryValid = false
                    allValid = false
                    error = e.message ?: "read error"
                }

                val computedCrc = crc.value
                val storedCrc = entry.crc
                if (computedCrc != storedCrc && entryValid) {
                    entryValid = false
                    allValid = false
                    error = "CRC-32 mismatch: computed ${computedCrc.toString(16)}, stored ${storedCrc.toString(16)}"
                }

                totalBytes += size
                reports.add(EntryReport(
                    name = entry.name,
                    crc32 = computedCrc.toLong(),
                    size = size,
                    valid = entryValid,
                    error = error
                ))
            }

            return ArchiveReport(
                valid = allValid && totalBytes <= MAX_TOTAL_BYTES,
                entries = reports.toList(),
                totalEntries = reports.size,
                totalBytes = totalBytes
            )
        }
    }
}
