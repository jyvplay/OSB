// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Clean-room hardened ZIP extraction firewall (`ZipSafeExtract`).
 *
 * Defends `:format:archive`, `:format:ooxml`, and `:format:lok` imports against all 5 classic archive attacks:
 * 1. **Zip-Slip traversal**: refuses `..` parent traversal, leading `/`, Windows `C:\` drive prefixes,
 *    backslashes `\`, and NUL `\u0000` bytes.
 * 2. **Symlink escape**: inspects the Central Directory (`0x02014B50`) POSIX `externalFileAttributes`
 *    upper 16 bits (`mode & 0xF000 == 0xA000` `S_IFLNK`, `0x6000` `S_IFBLK`, `0x2000` `S_IFCHR`, `0x1000` `S_IFIFO`)
 *    and refuses symbolic links and device nodes before extracting a single byte!
 * 3. **Zip-Bomb amplification**: enforces `maxEntries`, `maxEntryBytes`, `maxTotalBytes`, AND streaming
 *    per-entry compression ratio `uncompressed / max(1, compressed) <= maxCompressionRatio`!
 * 4. **Duplicate case-insensitive path collision**: refuses `Readme.txt` + `readme.txt` overwrite ambiguity.
 */
object ZipSafeExtract {
    const val SCOPE =
        "Hardened ZIP extraction firewall (Zip-Slip path guard, Central-Directory S_IFLNK symlink & device-node refusal, streaming Zip-Bomb ratio/byte/count caps, case-collision refusal); AES-encrypted ZIP out of scope."

    const val CEN_SIG = 0x02014B50
    const val S_IFMT = 0xF000
    const val S_IFLNK = 0xA000
    const val S_IFBLK = 0x6000
    const val S_IFCHR = 0x2000
    const val S_IFIFO = 0x1000

    data class Policy(
        val maxEntries: Int = 1_024,
        val maxEntryBytes: Int = 16 * 1024 * 1024,
        val maxTotalBytes: Int = 64 * 1024 * 1024,
        val maxCompressionRatio: Double = 100.0,
    )

    class UnsafeArchiveException(val reason: String, message: String) : IllegalArgumentException(message)

    fun extractSafe(zipBytes: ByteArray, policy: Policy = Policy()): Map<String, ByteArray> {
        inspectCentralDirectoryForSpecialModes(zipBytes, policy)
        val out = linkedMapOf<String, ByteArray>()
        val lowerSeen = hashSetOf<String>()
        var totalBytes = 0
        var count = 0

        ZipInputStream(ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                count++
                if (count > policy.maxEntries) {
                    throw UnsafeArchiveException("MAX_ENTRIES", "Archive exceeds maxEntries (${policy.maxEntries})")
                }
                val cleanName = sanitizeRelativeEntryPath(entry.name)
                if (entry.isDirectory) continue

                val lower = cleanName.lowercase(Locale.ROOT)
                if (!lowerSeen.add(lower)) {
                    throw UnsafeArchiveException("CASE_COLLISION", "Duplicate/case-colliding ZIP entry: $cleanName")
                }

                val baos = ByteArrayOutputStream()
                val tmp = ByteArray(4096)
                while (true) {
                    val n = zis.read(tmp)
                    if (n < 0) break
                    if (baos.size() + n > policy.maxEntryBytes) {
                        throw UnsafeArchiveException("MAX_ENTRY_BYTES", "Entry '$cleanName' exceeds ${policy.maxEntryBytes} bytes")
                    }
                    if (totalBytes + n > policy.maxTotalBytes) {
                        throw UnsafeArchiveException("MAX_TOTAL_BYTES", "Archive exceeds ${policy.maxTotalBytes} total bytes")
                    }
                    baos.write(tmp, 0, n)
                    totalBytes += n
                }

                val compSize = entry.compressedSize
                if (compSize > 0 && baos.size() > 1024) {
                    val ratio = baos.size().toDouble() / compSize.toDouble()
                    if (ratio > policy.maxCompressionRatio) {
                        throw UnsafeArchiveException(
                            "COMPRESSION_RATIO",
                            "Zip-bomb ratio ${"%.1f".format(Locale.US, ratio)} exceeds ${policy.maxCompressionRatio} on '$cleanName'",
                        )
                    }
                }
                out[cleanName] = baos.toByteArray()
            }
        }
        return out
    }

    fun sanitizeRelativeEntryPath(rawName: String): String {
        if (rawName.isEmpty() || rawName.contains('\u0000')) {
            throw UnsafeArchiveException("NUL_OR_EMPTY", "Empty or NUL-containing archive path")
        }
        if (rawName.startsWith("/") || rawName.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(rawName)) {
            throw UnsafeArchiveException("PATH_TRAVERSAL", "Absolute, backslash, or drive-prefixed archive path: $rawName")
        }
        val trimmed = rawName.trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val parts = trimmed.split('/')
        for (seg in parts) {
            if (seg.isEmpty() || seg == "." || seg == "..") {
                throw UnsafeArchiveException("PATH_TRAVERSAL", "Dot/parent traversal segment in archive path: $rawName")
            }
        }
        return parts.joinToString("/")
    }

    private fun inspectCentralDirectoryForSpecialModes(zipBytes: ByteArray, policy: Policy) {
        val buf = ByteBuffer.wrap(zipBytes).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        var cenEntries = 0
        while (i <= zipBytes.size - 46) {
            if (buf.getInt(i) == CEN_SIG) {
                cenEntries++
                if (cenEntries > policy.maxEntries) {
                    throw UnsafeArchiveException("MAX_ENTRIES", "Central directory exceeds maxEntries (${policy.maxEntries})")
                }
                val compSize = buf.getInt(i + 20).toLong() and 0xFFFF_FFFFL
                val uncompSize = buf.getInt(i + 24).toLong() and 0xFFFF_FFFFL
                val nameLen = buf.getShort(i + 28).toInt() and 0xFFFF
                val extraLen = buf.getShort(i + 30).toInt() and 0xFFFF
                val commentLen = buf.getShort(i + 32).toInt() and 0xFFFF
                val extAttr = buf.getInt(i + 38)
                val unixMode = (extAttr ushr 16) and 0xFFFF
                val fileType = unixMode and S_IFMT
                val nameStart = i + 46
                val entryName = if (nameStart + nameLen <= zipBytes.size) {
                    String(zipBytes, nameStart, nameLen, StandardCharsets.UTF_8)
                } else "<truncated>"

                sanitizeRelativeEntryPath(entryName)

                if (fileType == S_IFLNK || fileType == S_IFBLK || fileType == S_IFCHR || fileType == S_IFIFO) {
                    throw UnsafeArchiveException(
                        "SYMLINK_OR_SPECIAL_MODE",
                        "Refusing POSIX symlink/device mode 0x${unixMode.toString(16)} on '$entryName'",
                    )
                }
                if (compSize > 0 && uncompSize > 1024 && uncompSize.toDouble() / compSize.toDouble() > policy.maxCompressionRatio) {
                    throw UnsafeArchiveException(
                        "COMPRESSION_RATIO",
                        "Central directory ratio exceeds ${policy.maxCompressionRatio} on '$entryName'",
                    )
                }
                i = nameStart + nameLen + extraLen + commentLen
            } else {
                i++
            }
        }
    }
}
