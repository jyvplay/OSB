// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/** One ustar regular-file header: path, size, mode, mtime. */
data class TarEntry(val name: String, val size: Long, val mode: Int, val mtime: Long)

/**
 * POSIX ustar header reader (one 512-byte record). Parses name/prefix,
 * octal size/mode/mtime, verifies the checksum with the checksum field
 * treated as spaces, and accepts regular files only (typeflag '0'/NUL).
 * Deliberate refusals: pre-POSIX/v7 magic, GNU magic, non-regular
 * typeflags (in particular GNU 'L'/'K' longname records, which would
 * silently rename the following entry), malformed octal, and checksum
 * mismatch. Multi-record layout (data blocks, end marker) stays with the
 * caller; [dataBlocks] gives the record count per entry.
 */
object TarHeader {
    const val RECORD = 512
    const val MAX_SIZE = 8L * 1024 * 1024 * 1024

    fun dataBlocks(size: Long): Long {
        require(size in 0..MAX_SIZE)
        return (size + RECORD - 1) / RECORD
    }

    fun parse(record: ByteArray): TarEntry {
        require(record.size == RECORD)
        fun text(at: Int, len: Int): String {
            var end = at
            while (end < at + len && record[end] != 0.toByte()) end++
            val s = String(record, at, end - at, Charsets.UTF_8)
            require(s.none { it.isISOControl() })
            return s
        }
        fun octal(at: Int, len: Int, max: Long): Long {
            val raw = String(record, at, len, Charsets.US_ASCII).trim { it == '\u0000' || it == ' ' }
            require(raw.isNotEmpty() && raw.length <= 12 && raw.all { it in '0'..'7' })
            val v = raw.toLong(8)
            require(v in 0..max)
            return v
        }
        // Checksum: unsigned byte sum with the 8 chksum bytes read as spaces.
        var sum = 0L
        for (i in record.indices) {
            sum += if (i in 148..155) 0x20 else record[i].toInt() and 0xFF
        }
        val stored = octal(148, 8, 0x1FFFF)
        require(sum == stored) { "checksum $sum != stored $stored" }
        val magic = String(record, 257, 6, Charsets.US_ASCII)
        require(magic == "ustar\u0000") { "not ustar" }
        require(record[263] == '0'.code.toByte() && record[264] == '0'.code.toByte()) { "not ustar version 00" }
        val type = record[156]
        require(type == '0'.code.toByte() || type == 0.toByte()) { "not a regular file" }
        val name = text(0, 100)
        val prefix = text(345, 155)
        require(name.isNotEmpty() && name.length <= 100)
        val full = if (prefix.isEmpty()) name else "$prefix/$name"
        require(full.length <= 256)
        val size = octal(124, 12, MAX_SIZE)
        val mode = octal(100, 8, 0xFFF).toInt()
        val mtime = octal(136, 12, 0xFFFFFFFFL)
        return TarEntry(full, size, mode, mtime)
    }
}
