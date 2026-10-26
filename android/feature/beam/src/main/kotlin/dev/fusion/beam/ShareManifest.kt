// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import java.security.MessageDigest

/**
 * Validated share manifest: the file set one Beam send offers to one receiver.
 * Pure offer description only; transport/consent live elsewhere. All names are
 * bare file names (no directories, no separators) so a manifest can never
 * address outside the receiver's chosen directory.
 */
data class BeamEntry(val name: String, val bytes: Long, val mime: String, val sha256: String)

class ShareManifest private constructor(val entries: List<BeamEntry>) {
    companion object {
        const val MAX_ENTRIES = 64
        const val MAX_NAME = 255
        const val MAX_BYTES_EACH = 8L * 1024 * 1024 * 1024
        const val MAX_TOTAL = 16L * 1024 * 1024 * 1024
        const val MAGIC = 0x53484D31
        private val MIME = Regex("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*")
        private val HEX64 = Regex("[0-9a-f]{64}")

        fun validName(s: String) = s.isNotEmpty() && s.length <= MAX_NAME &&
            s.none { it == '/' || it == '\\' || it.code < 0x20 || it.code == 0x7F } &&
            s != "." && s != ".." && !s.endsWith(".") && !s.startsWith(" ")

        fun validMime(s: String) = s.length <= 127 && MIME.matches(s)
        fun validSha(s: String) = HEX64.matches(s)

        fun of(entries: List<BeamEntry>): ShareManifest {
            require(entries.isNotEmpty() && entries.size <= MAX_ENTRIES)
            val names = HashSet<String>()
            var total = 0L
            for (e in entries) {
                require(validName(e.name)); require(validMime(e.mime)); require(validSha(e.sha256))
                require(e.bytes in 0..MAX_BYTES_EACH)
                require(names.add(e.name)) { "duplicate name" }
                total += e.bytes; require(total <= MAX_TOTAL)
            }
            return ShareManifest(entries.toList())
        }

        fun readFrame(bytes: ByteArray): ShareManifest {
            require(bytes.size in 8..1_200_000)
            val b = bytes.inputStream()
            fun i32(): Int {
                val v = ByteArray(4); require(b.read(v) == 4)
                return ((v[0].toInt() and 0xFF) shl 24) or ((v[1].toInt() and 0xFF) shl 16) or
                    ((v[2].toInt() and 0xFF) shl 8) or (v[3].toInt() and 0xFF)
            }
            fun i64(): Long = ((i32().toLong() and 0xFFFFFFFFL) shl 32) or (i32().toLong() and 0xFFFFFFFFL)
            fun str(n: Int): String {
                require(n in 0..4096)
                val v = ByteArray(n); require(b.read(v) == n); return String(v, Charsets.UTF_8)
            }
            require(i32() == MAGIC)
            val n = i32(); require(n in 1..MAX_ENTRIES)
            val out = ArrayList<BeamEntry>(n)
            repeat(n) {
                val name = str(i32()); val size = i64(); val mime = str(i32()); val sha = str(i32())
                out.add(BeamEntry(name, size, mime, sha))
            }
            require(b.read() == -1)
            return of(out)
        }
    }

    val totalBytes: Long get() = entries.sumOf { it.bytes }
    fun find(name: String): BeamEntry? = entries.firstOrNull { it.name == name }

    fun digest(): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (e in entries.sortedBy { it.name })
            md.update("${e.name}\n${e.bytes}\n${e.mime}\n${e.sha256}\n".toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun writeFrame(): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        fun i32(v: Int) { o.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())) }
        fun i64(v: Long) { i32((v shr 32).toInt()); i32(v.toInt()) }
        fun str(s: String) { val v = s.toByteArray(Charsets.UTF_8); i32(v.size); o.write(v) }
        i32(MAGIC); i32(entries.size)
        for (e in entries) { str(e.name); i64(e.bytes); str(e.mime); str(e.sha256) }
        return o.toByteArray()
    }
}
