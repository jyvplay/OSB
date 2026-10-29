package dev.fusion.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream

/**
 * Clean-room Bit-for-Bit Reproducible POSIX `ustar` `.tar.gz` Writer & Tar-Slip-Safe Reader
 * (Apache-2.0).
 *
 * Guarantees identical SHA-256 across hosts/OSes/timestamps:
 * - Lexicographically sorts entry paths, strips leading `/`, refuses `..` traversal,
 * - Pins `mode=0644`, `uid=0`, `gid=0`, `mtime=0`, `uname=""`, `gname=""`,
 * - Emits RFC 1952 GZIP header with `MTIME=0` and `OS=255` (`unknown`) so `java.util.zip.GZIPOutputStream`'s
 *   OS-byte/timestamp variance cannot leak host metadata!
 */
object DeterministicReproducibleTarGzWriter {
    const val SCOPE =
        "Bit-for-bit reproducible POSIX ustar .tar.gz writer/reader (sorted paths, mtime=0, uid/gid=0, RFC 1952 OS=255 zero-epoch GZIP)"

    fun writeTarGz(files: Map<String, ByteArray>): ByteArray {
        require(files.isNotEmpty()) { "Archive requires >= 1 file" }
        val tarOut = ByteArrayOutputStream()
        for (rawPath in files.keys.sorted()) {
            val path = sanitizeRelativePath(rawPath)
            val content = files.getValue(rawPath)
            require(path.toByteArray(StandardCharsets.UTF_8).size <= 100) { "ustar path exceeds 100 bytes: $path" }
            tarOut.write(buildUstarHeader(path, content.size.toLong()))
            tarOut.write(content)
            val pad = (512 - (content.size % 512)) % 512
            if (pad > 0) tarOut.write(ByteArray(pad))
        }
        // Two 512-byte zero EOF blocks per POSIX.1-1988
        tarOut.write(ByteArray(1024))
        return deterministicGzip(tarOut.toByteArray())
    }

    fun readTarGz(tarGzBytes: ByteArray): Map<String, ByteArray> {
        val tarBytes = GZIPInputStream(ByteArrayInputStream(tarGzBytes)).use { it.readBytes() }
        require(tarBytes.size >= 1024 && tarBytes.size % 512 == 0) { "Invalid POSIX tar block alignment" }
        val result = LinkedHashMap<String, ByteArray>()
        var pos = 0
        while (pos + 512 <= tarBytes.size) {
            if ((pos until pos + 512).all { tarBytes[it].toInt() == 0 }) break
            val name = readNullTermAscii(tarBytes, pos, 100)
            val clean = sanitizeRelativePath(name)
            val typeFlag = tarBytes[pos + 156].toInt().toChar()
            require(typeFlag == '0' || typeFlag == '\u0000') {
                "Refusing non-regular tar entry '$clean' of typeFlag='$typeFlag' (symlink/hardlink/device defense)"
            }
            val sizeOctal = readNullTermAscii(tarBytes, pos + 124, 12).trim()
            val size = sizeOctal.toLong(8).toInt()
            pos += 512
            require(size >= 0 && pos + size <= tarBytes.size) { "Truncated tar entry '$clean'" }
            result[clean] = tarBytes.copyOfRange(pos, pos + size)
            pos += ((size + 511) / 512) * 512
        }
        return result
    }

    private fun buildUstarHeader(path: String, byteSize: Long): ByteArray {
        val h = ByteArray(512)
        writeAscii(h, 0, path, 100)
        writeOctal(h, 100, 0b110_100_100L, 8) // 0644
        writeOctal(h, 108, 0L, 8)             // uid = 0
        writeOctal(h, 116, 0L, 8)             // gid = 0
        writeOctal(h, 124, byteSize, 12)      // size
        writeOctal(h, 136, 0L, 12)            // mtime = 0
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        h[156] = '0'.code.toByte()            // regular file
        writeAscii(h, 257, "ustar", 6)
        h[263] = '0'.code.toByte()
        h[264] = '0'.code.toByte()
        val chksum = h.sumOf { it.toInt() and 0xFF }.toLong()
        val chkStr = chksum.toString(8).padStart(6, '0')
        writeAscii(h, 148, chkStr, 6)
        h[154] = 0
        h[155] = ' '.code.toByte()
        return h
    }

    private fun deterministicGzip(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        val compressed = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
        val crc = CRC32().apply { update(raw) }.value.toInt()
        val gz = ByteArrayOutputStream(10 + compressed.size + 8)
        // RFC 1952: ID1=0x1f, ID2=0x8b, CM=8 (deflate), FLG=0, MTIME=00000000, XFL=0, OS=0xFF (unknown)
        gz.write(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()))
        gz.write(compressed)
        gz.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).putInt(raw.size).array())
        return gz.toByteArray()
    }

    private fun sanitizeRelativePath(p: String): String {
        val norm = p.replace('\\', '/').trim()
        require(norm.isNotEmpty() && !norm.startsWith("/") && norm.split('/').none { it == ".." || it.isEmpty() }) {
            "Unsafe tar path '$p'"
        }
        return norm
    }

    private fun writeAscii(buf: ByteArray, offset: Int, s: String, maxLen: Int) {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(b, 0, buf, offset, minOf(b.size, maxLen))
    }

    private fun writeOctal(buf: ByteArray, offset: Int, v: Long, fieldWidth: Int) {
        val digits = v.toString(8).padStart(fieldWidth - 1, '0')
        writeAscii(buf, offset, digits, fieldWidth - 1)
        buf[offset + fieldWidth - 1] = 0
    }

    private fun readNullTermAscii(buf: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        while (end < offset + maxLen && buf[end].toInt() != 0) end++
        return String(buf, offset, end - offset, StandardCharsets.UTF_8)
    }
}
