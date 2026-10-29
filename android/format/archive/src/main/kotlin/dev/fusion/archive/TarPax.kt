// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Clean-room POSIX.1-2001 (`pax` `x` extended header + `ustar`) deterministic TAR reader/writer.
 *
 * Security & privacy invariants:
 * - Zero-epoch reproducible output (`mtime=0`, `uid=0`, `gid=0`, `uname=""`, `gname=""`) — zero
 *   host username or timestamp leakage (`mat2` / reproducible builds discipline).
 * - Automatic POSIX `pax` (`typeflag = 'x'`) extended header emission and parsing for UTF-8
 *   paths >100 bytes or non-ASCII filenames.
 * - Refuses path traversal (`..`, leading `/`, `\`, NUL bytes), symlinks/hardlinks/device nodes
 *   (`typeflag` outside `'0'`, `'\u0000'`, `'5'`, `'x'`), header checksum mismatches, and archives
 *   exceeding [MAX_TAR_BYTES].
 */
object TarPax {
    const val SCOPE =
        "POSIX.1-2001 ustar + pax ('x' extended path) deterministic zero-epoch TAR reader/writer with traversal/symlink refusal and checksum verification; gzip/xz outer stream framing out of scope."

    const val BLOCK = 512
    const val MAX_TAR_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRIES = 2_048

    data class TarFile(val path: String, val data: ByteArray, val mode: Int = 0b110_100_100) { // 0644
        init {
            validateSafePath(path)
            require(mode in 0..0b111_111_111) { "invalid POSIX mode $mode" }
        }
    }

    fun writeDeterministic(files: List<TarFile>): ByteArray {
        require(files.size <= MAX_ENTRIES) { "TAR exceeds $MAX_ENTRIES entries" }
        val sorted = files.sortedBy { it.path }
        for (i in 1 until sorted.size) {
            require(sorted[i - 1].path != sorted[i].path) { "duplicate TAR path '${sorted[i].path}'" }
        }
        val out = ByteArrayOutputStream()
        for (f in sorted) {
            val pathBytes = f.path.toByteArray(StandardCharsets.UTF_8)
            val needsPax = pathBytes.size > 100 || pathBytes.any { (it.toInt() and 0x80) != 0 }
            if (needsPax) {
                val paxRecord = formatPaxLine("path", f.path).toByteArray(StandardCharsets.UTF_8)
                writeEntry(out, "PaxHeader/${asciiTail(f.path, 60)}", 'x', 0b110_100_100, paxRecord)
            }
            val shortName = if (needsPax) asciiTail(f.path, 99) else f.path
            writeEntry(out, shortName, '0', f.mode, f.data)
        }
        // Two 512-byte zero EOF blocks per POSIX standard
        out.write(ByteArray(BLOCK * 2))
        return out.toByteArray()
    }

    fun read(tarBytes: ByteArray): List<TarFile> {
        require(tarBytes.size <= MAX_TAR_BYTES && tarBytes.size % BLOCK == 0) {
            "invalid TAR size ${tarBytes.size} (must be multiple of 512 <= $MAX_TAR_BYTES)"
        }
        val out = ArrayList<TarFile>()
        var pos = 0
        var pendingPaxPath: String? = null
        val seenPaths = HashSet<String>()

        while (pos + BLOCK <= tarBytes.size) {
            val hdr = tarBytes.copyOfRange(pos, pos + BLOCK)
            pos += BLOCK
            if (hdr.all { it == 0.toByte() }) break

            verifyHeaderChecksum(hdr)
            val magic = readAscii(hdr, 257, 5)
            require(magic == "ustar" || magic.isEmpty()) { "unsupported TAR magic '$magic'" }

            val size = parseOctal(hdr, 124, 12)
            require(size >= 0L && size <= MAX_TAR_BYTES && pos + size <= tarBytes.size) {
                "TAR entry size $size out of bounds"
            }
            val typeFlag = (hdr[156].toInt() and 0xFF).toChar()
            val mode = parseOctal(hdr, 100, 8).toInt() and 0b111_111_111
            val payload = tarBytes.copyOfRange(pos, pos + size.toInt())
            val paddedBlocks = ((size + BLOCK - 1) / BLOCK) * BLOCK
            pos += paddedBlocks.toInt()

            when (typeFlag) {
                'x' -> {
                    pendingPaxPath = parsePaxPath(payload)
                }
                '5' -> {
                    pendingPaxPath = null // directory entry skipped safely after validating path
                    val dirPath = readHeaderPath(hdr).trimEnd('/')
                    if (dirPath.isNotEmpty()) validateSafePath(dirPath)
                }
                '0', '\u0000' -> {
                    val effectivePath = pendingPaxPath ?: readHeaderPath(hdr)
                    pendingPaxPath = null
                    validateSafePath(effectivePath)
                    require(seenPaths.add(effectivePath)) { "duplicate TAR entry '$effectivePath'" }
                    require(out.size < MAX_ENTRIES) { "TAR entry count exceeds $MAX_ENTRIES" }
                    out.add(TarFile(effectivePath, payload, mode))
                }
                else -> throw IllegalArgumentException(
                    "unsafe or unsupported TAR typeflag '$typeFlag' (symlinks/hardlinks/devices refused)",
                )
            }
        }
        return out
    }

    fun validateSafePath(path: String) {
        require(path.isNotBlank() && '\u0000' !in path && '\\' !in path) { "invalid characters in TAR path '$path'" }
        require(!path.startsWith("/") && !path.endsWith("/")) { "TAR path '$path' must be relative regular-file path" }
        val segs = path.split('/')
        require(segs.all { it.isNotEmpty() && it != "." && it != ".." }) {
            "path traversal segment refused in TAR path '$path'"
        }
    }

    private fun writeEntry(out: ByteArrayOutputStream, name: String, typeFlag: Char, mode: Int, data: ByteArray) {
        val hdr = ByteArray(BLOCK)
        writeAscii(hdr, 0, 100, name)
        writeOctal(hdr, 100, 8, mode.toLong())
        writeOctal(hdr, 108, 8, 0L) // uid = 0
        writeOctal(hdr, 116, 8, 0L) // gid = 0
        writeOctal(hdr, 124, 12, data.size.toLong())
        writeOctal(hdr, 136, 12, 0L) // mtime = 0 (1970-01-01T00:00:00Z)
        for (i in 148 until 156) hdr[i] = ' '.code.toByte()
        hdr[156] = typeFlag.code.toByte()
        writeAscii(hdr, 257, 6, "ustar")
        hdr[263] = '0'.code.toByte(); hdr[264] = '0'.code.toByte()
        var sum = 0L
        for (b in hdr) sum += (b.toInt() and 0xFF)
        val chk = sum.toString(8).padStart(6, '0')
        for (i in 0 until 6) hdr[148 + i] = chk[i].code.toByte()
        hdr[154] = 0
        hdr[155] = ' '.code.toByte()

        out.write(hdr)
        out.write(data)
        val rem = data.size % BLOCK
        if (rem != 0) out.write(ByteArray(BLOCK - rem))
    }

    private fun formatPaxLine(key: String, value: String): String {
        // POSIX pax record: "%d %s=%s\n" where %d is total UTF-8 byte length including %d itself!
        val bodyBytes = " $key=$value\n".toByteArray(StandardCharsets.UTF_8).size
        var digits = 1
        while (digits + bodyBytes >= pow10(digits)) digits++
        val total = digits + bodyBytes
        return "$total $key=$value\n"
    }

    private fun pow10(d: Int): Int {
        var r = 1
        repeat(d) { r *= 10 }
        return r
    }

    private fun parsePaxPath(bytes: ByteArray): String? {
        var pos = 0
        var foundPath: String? = null
        while (pos < bytes.size) {
            val sp = (pos until bytes.size).firstOrNull { bytes[it] == ' '.code.toByte() }
                ?: throw IllegalArgumentException("malformed PAX extended header")
            val recLen = String(bytes, pos, sp - pos, StandardCharsets.US_ASCII).toInt()
            require(recLen > (sp - pos + 2) && pos + recLen <= bytes.size && bytes[pos + recLen - 1] == '\n'.code.toByte()) {
                "invalid PAX record length $recLen"
            }
            val kv = String(bytes, sp + 1, recLen - (sp - pos) - 2, StandardCharsets.UTF_8)
            val eq = kv.indexOf('=')
            require(eq > 0) { "missing '=' in PAX record" }
            if (kv.substring(0, eq) == "path") foundPath = kv.substring(eq + 1)
            pos += recLen
        }
        return foundPath
    }

    private fun verifyHeaderChecksum(hdr: ByteArray) {
        val stored = parseOctal(hdr, 148, 8)
        var unsignedSum = 0L
        for (i in 0 until BLOCK) {
            unsignedSum += if (i in 148 until 156) 32L else (hdr[i].toInt() and 0xFF).toLong()
        }
        require(stored == unsignedSum) { "TAR header checksum mismatch (expected $stored, got $unsignedSum)" }
    }

    private fun readHeaderPath(hdr: ByteArray): String {
        val name = readAscii(hdr, 0, 100)
        val prefix = readAscii(hdr, 345, 155)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun asciiTail(path: String, maxChars: Int): String {
        val clean = buildString(path.length) {
            for (c in path) append(if (c.code in 0x20..0x7E) c else '_')
        }
        return if (clean.length <= maxChars) clean else clean.substring(clean.length - maxChars).trimStart('/')
    }

    private fun readAscii(buf: ByteArray, off: Int, maxLen: Int): String {
        var end = off
        val lim = off + maxLen
        while (end < lim && buf[end] != 0.toByte()) end++
        return String(buf, off, end - off, StandardCharsets.UTF_8)
    }

    private fun writeAscii(buf: ByteArray, off: Int, maxLen: Int, s: String) {
        val b = s.toByteArray(StandardCharsets.US_ASCII)
        require(b.size <= maxLen) { "field '$s' exceeds $maxLen bytes" }
        System.arraycopy(b, 0, buf, off, b.size)
    }

    private fun parseOctal(buf: ByteArray, off: Int, len: Int): Long {
        val raw = readAscii(buf, off, len).trim(' ', '\u0000')
        if (raw.isEmpty()) return 0L
        return raw.toLong(8)
    }

    private fun writeOctal(buf: ByteArray, off: Int, len: Int, v: Long) {
        val digits = len - 1
        val oct = v.toString(8).padStart(digits, '0')
        require(oct.length == digits) { "octal $v overflows $digits digits" }
        for (i in 0 until digits) buf[off + i] = oct[i].code.toByte()
        buf[off + digits] = 0
    }
}
