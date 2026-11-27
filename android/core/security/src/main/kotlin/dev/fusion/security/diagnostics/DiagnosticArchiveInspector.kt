// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security.diagnostics

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * R176 read-only diagnostic bundle verification. No extraction, text execution,
 * trust inference, signature or anonymity claim. An attacker can recompute the
 * checksum; it detects corruption/contract violations, not an adversarial author.
 * Inspect only an app-private bounded copy of a user-selected file. This class
 * validates content, not every possible ZIP encoding or provider durability.
 */
object DiagnosticArchiveInspector {
    data class Report(val records: Int, val utf8Bytes: Int, val sha256: String) {
        val summary: String get() = "FDE1 content verified: $records records, $utf8Bytes UTF-8 bytes. SHA-256: $sha256. Integrity is not authenticity or anonymity."
    }
    private const val MAX_TEXT = 1024 * 1024 + 32 * 1024
    private val KEY = Regex("[a-z][a-z0-9_.-]{0,63}")

    fun inspect(path: Path): Report {
        val size = Files.size(path)
        require(size in 22..DiagnosticSaveWorkflow.MAX_ARCHIVE_BYTES.toLong()) { "archive size" }
        // Require an un-commented end record at EOF. ZipFile alone accepts
        // trailing junk. No ZIP64, split archive or prepended executable allowed.
        Files.newByteChannel(path).use { channel ->
            val signature = ByteBuffer.allocate(4)
            while (signature.hasRemaining()) require(channel.read(signature) > 0) { "truncated header" }
            require(signature.array().contentEquals(byteArrayOf(0x50, 0x4b, 3, 4))) { "local header" }
            channel.position(size - 22)
            val end = ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            while (end.hasRemaining()) require(channel.read(end) > 0) { "truncated end" }
            val bytes = end.array()
            require(bytes.sliceArray(0..3).contentEquals(byteArrayOf(0x50, 0x4b, 5, 6))) { "end header" }
            require(end.getShort(4).toInt() == 0 && end.getShort(6).toInt() == 0) { "split archive" }
            require(end.getShort(8).toInt() == 2 && end.getShort(10).toInt() == 2) { "entry count" }
            val centralSize = end.getInt(12).toLong() and 0xffffffffL
            val centralAt = end.getInt(16).toLong() and 0xffffffffL
            require(centralAt > 0 && centralSize > 0 && centralAt + centralSize == size - 22) { "central directory bounds" }
            require(end.getShort(20).toInt() == 0) { "archive comment" }
        }
        ZipFile(path.toFile(), Charsets.UTF_8).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size == 2 && entries.map { it.name }.toSet() == setOf("diagnostics.txt", "manifest.txt")) { "entries" }
            fun read(name: String, limit: Int): ByteArray {
                val entry = entries.single { it.name == name }
                require(!entry.isDirectory && entry.size in 0..limit.toLong()) { "entry size" }
                require(entry.method == java.util.zip.ZipEntry.STORED || entry.method == java.util.zip.ZipEntry.DEFLATED) { "compression" }
                val bytes = zip.getInputStream(entry).use { it.readNBytes(limit + 1) }
                require(bytes.size <= limit && bytes.size.toLong() == entry.size) { "expanded size" }
                require(CRC32().apply { update(bytes) }.value == entry.crc) { "CRC" }
                return bytes
            }
            val content = read("diagnostics.txt", MAX_TEXT)
            val manifest = strictUtf8(read("manifest.txt", 4096))
            val text = strictUtf8(content)
            val lines = if (text.isEmpty()) emptyList() else text.split('\n')
            require(lines.size <= DiagnosticExportAllowlist.MAX_RECORDS) { "too many records" }
            var previous = ""
            for (line in lines) {
                val eq = line.indexOf('=')
                require(eq > 0) { "record" }
                val key = line.substring(0, eq)
                val value = line.substring(eq + 1)
                require(KEY.matches(key) && key > previous) { "unsorted/duplicate key" }
                require(value.length <= DiagnosticExportAllowlist.MAX_VALUE) { "value size" }
                require(value.none { unsafe(it) }) { "control in value" }
                previous = key
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
            require(manifest == "format=FDE1\nsha256=$hash\nbytes=${content.size}\nwarning=${DiagnosticExportSession.WARNING}\n") {
                "manifest mismatch"
            }
            return Report(lines.size, content.size, hash)
        }
    }

    internal fun unsafe(c: Char): Boolean = c.isISOControl() || c in '\u2028'..'\u202e' ||
        c in '\u2066'..'\u2069' || c == '\u200e' || c == '\u200f' || c == '\u061c'

    private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
