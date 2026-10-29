// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Clean-room Git Packfile Index v2 (`\377tOc`, version `2`) encoder & $O(\log N)$ fanout-narrowed
 * binary-search reader (`PackIndexCompact`).
 *
 * Implements the exact Git `.idx` v2 binary layout (`Documentation/gitformat-pack.txt`):
 * - 4-byte magic `0xFF, 't', 'O', 'c'` (`0xFF744F63`) + 4-byte big-endian version `2`.
 * - 256-entry first-level fanout table (`256 * 4` bytes, cumulative count of objects with `oid[0] <= b`).
 * - Sorted 20-byte object IDs (`N * 20` bytes).
 * - Parallel 4-byte CRC32 table (`N * 4` bytes).
 * - Parallel 4-byte pack offset table (`N * 4` bytes; MSB=1 selects 8-byte large-offset table `>= 2 GiB`).
 * - 20-byte packfile checksum + 20-byte `.idx` self-SHA-1 trailer verified on open!
 */
object PackIndexCompact {
    const val SCOPE =
        "Git Packfile Index v2 (0xFF744F63) binary writer + 256-fanout O(log N) lookup + 64-bit large-offset (>2 GiB) table + SHA-1 self-checksum verifier; SHA-256 object-format v3 out of scope."

    const val MAGIC = -0x008bb09d // 0xFF744F63 signed 32-bit big-endian
    const val VERSION = 2

    data class PackEntry(
        val oidHex: String,
        val crc32: Int,
        val packOffset: Long,
    ) {
        val oidBytes: ByteArray = hexToBytes(oidHex)
        init {
            require(oidBytes.size == 20) { "SHA-1 OID must be 40 hex chars (20 bytes): $oidHex" }
            require(packOffset >= 0L) { "Negative packOffset: $packOffset" }
        }
    }

    data class LookupResult(val oidHex: String, val crc32: Int, val packOffset: Long)

    fun writeIdxV2(entries: List<PackEntry>, packChecksumHex: String): ByteArray {
        val packSha = hexToBytes(packChecksumHex)
        require(packSha.size == 20) { "packChecksumHex must be 40 hex chars" }

        val sorted = entries.sortedWith { a, b -> compareUnsignedLex(a.oidBytes, b.oidBytes) }
        for (i in 1 until sorted.size) {
            require(!sorted[i - 1].oidBytes.contentEquals(sorted[i].oidBytes)) {
                "Duplicate OID in Git pack index: ${sorted[i].oidHex}"
            }
        }

        val n = sorted.size
        val fanout = IntArray(256)
        for (e in sorted) {
            val firstByte = e.oidBytes[0].toInt() and 0xFF
            for (b in firstByte..255) fanout[b]++
        }

        val largeOffsets = ArrayList<Long>()
        val offsetWords = IntArray(n) { idx ->
            val off = sorted[idx].packOffset
            if (off < 0x8000_0000L) {
                off.toInt()
            } else {
                val largeIdx = largeOffsets.size
                largeOffsets.add(off)
                0x8000_0000.toInt() or largeIdx
            }
        }

        val bodyLen = 8 + 256 * 4 + n * 20 + n * 4 + n * 4 + largeOffsets.size * 8 + 20
        val out = ByteArray(bodyLen + 20)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(MAGIC)
        bb.putInt(VERSION)
        for (f in fanout) bb.putInt(f)
        for (e in sorted) bb.put(e.oidBytes)
        for (e in sorted) bb.putInt(e.crc32)
        for (w in offsetWords) bb.putInt(w)
        for (lo in largeOffsets) bb.putLong(lo)
        bb.put(packSha)

        val idxSha = MessageDigest.getInstance("SHA-1").apply { update(out, 0, bodyLen) }.digest()
        System.arraycopy(idxSha, 0, out, bodyLen, 20)
        return out
    }

    class Reader(private val idxBytes: ByteArray) {
        val objectCount: Int
        private val fanout = IntArray(256)
        private val oidsOffset: Int
        private val crcOffset: Int
        private val off32Offset: Int
        private val off64Offset: Int

        init {
            require(idxBytes.size >= 8 + 256 * 4 + 40) { "Truncated Git .idx v2 file" }
            val bodyLen = idxBytes.size - 20
            val expectedSha = MessageDigest.getInstance("SHA-1").apply { update(idxBytes, 0, bodyLen) }.digest()
            val trailerSha = idxBytes.copyOfRange(bodyLen, idxBytes.size)
            require(MessageDigest.isEqual(expectedSha, trailerSha)) { "Git .idx v2 trailer SHA-1 mismatch" }

            val bb = ByteBuffer.wrap(idxBytes).order(ByteOrder.BIG_ENDIAN)
            require(bb.int == MAGIC && bb.int == VERSION) { "Invalid Git .idx v2 magic/version" }
            var prev = 0
            for (i in 0..255) {
                val c = bb.int
                require(c >= prev) { "Non-monotonic fanout table at $i" }
                fanout[i] = c
                prev = c
            }
            objectCount = fanout[255]
            oidsOffset = 8 + 256 * 4
            crcOffset = oidsOffset + objectCount * 20
            off32Offset = crcOffset + objectCount * 4
            off64Offset = off32Offset + objectCount * 4
            require(off64Offset <= bodyLen - 20) { "Truncated Git .idx v2 tables" }
        }

        fun find(oidHex: String): LookupResult? {
            val target = hexToBytes(oidHex)
            require(target.size == 20) { "OID must be 20 bytes" }
            val first = target[0].toInt() and 0xFF
            var lo = if (first == 0) 0 else fanout[first - 1]
            var hi = fanout[first] - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val cmp = compareAt(mid, target)
                when {
                    cmp < 0 -> lo = mid + 1
                    cmp > 0 -> hi = mid - 1
                    else -> return readEntryAt(mid)
                }
            }
            return null
        }

        private fun compareAt(idx: Int, target: ByteArray): Int {
            val base = oidsOffset + idx * 20
            for (k in 0 until 20) {
                val a = idxBytes[base + k].toInt() and 0xFF
                val b = target[k].toInt() and 0xFF
                if (a != b) return a - b
            }
            return 0
        }

        private fun readEntryAt(idx: Int): LookupResult {
            val bb = ByteBuffer.wrap(idxBytes).order(ByteOrder.BIG_ENDIAN)
            val oidSlice = idxBytes.copyOfRange(oidsOffset + idx * 20, oidsOffset + (idx + 1) * 20)
            val crc = bb.getInt(crcOffset + idx * 4)
            val rawOff = bb.getInt(off32Offset + idx * 4)
            val packOffset = if ((rawOff and 0x8000_0000.toInt()) == 0) {
                rawOff.toLong() and 0x7FFF_FFFFL
            } else {
                val largeIdx = rawOff and 0x7FFF_FFFF
                bb.getLong(off64Offset + largeIdx * 8)
            }
            return LookupResult(bytesToHex(oidSlice), crc, packOffset)
        }
    }

    private fun compareUnsignedLex(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai - bi
        }
        return 0
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.trim()
        require(s.length % 2 == 0) { "Odd hex length: ${s.length}" }
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun bytesToHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append("0123456789abcdef"[v ushr 4])
            append("0123456789abcdef"[v and 0x0F])
        }
    }
}
