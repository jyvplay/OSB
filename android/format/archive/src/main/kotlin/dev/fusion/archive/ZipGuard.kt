package dev.fusion.archive

import java.io.File
import java.util.Collections
import java.util.zip.ZipFile

data class ZipEntryInfo(val name: String, val size: Long, val dir: Boolean)

/**
 * Read-only ZIP listing with zip-slip and decompression-bomb guards.
 * Extraction is deliberately not exposed yet: a safe extractor needs the
 * same journal/rollback work that file move and delete are waiting on.
 */
object ZipGuard {
    const val MAX_ENTRIES = 10_000
    const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    const val MAX_RATIO = 100L

    fun list(zip: File): Result<List<ZipEntryInfo>> = runCatching {
        ZipFile(zip).use { archive ->
            val entries = Collections.list(archive.entries())
            require(entries.size <= MAX_ENTRIES) { "Too many entries (${entries.size})" }

            var uncompressed = 0L
            var compressed = 0L
            val out = entries.map { entry ->
                if (entry.size > 0) uncompressed += entry.size
                if (entry.compressedSize > 0) compressed += entry.compressedSize
                ZipEntryInfo(entry.name, entry.size.coerceAtLeast(0), entry.isDirectory)
            }

            require(uncompressed <= MAX_TOTAL) { "Archive expands to more than 2 GB" }
            if (compressed > 0 && uncompressed / compressed > MAX_RATIO) {
                throw IllegalArgumentException("Compression ratio looks like a zip bomb")
            }
            out.forEach { info ->
                require(!info.name.contains("..")) { "Unsafe entry path: ${info.name}" }
                require(!info.name.startsWith("/")) { "Absolute entry path: ${info.name}" }
                require(!info.name.contains('\\')) { "Windows-style path rejected: ${info.name}" }
            }
            out
        }
    }
}
