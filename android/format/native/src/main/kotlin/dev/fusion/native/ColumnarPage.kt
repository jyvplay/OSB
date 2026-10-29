// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room Parquet/Arrow-inspired Columnar Data Page Codec with Zone-Map Statistics.
 *
 * Encodes nullable `LONG` (delta + ZigZag varint) and `UTF8` (dictionary + RLE/index) columns
 * into compact binary pages carrying exact `[rowCount, nullCount, min, max]` zone-map headers
 * so analytical queries can skip entire pages (`mayContain`) without decoding payloads!
 */
object ColumnarPage {
    const val SCOPE =
        "Clean-room columnar data page codec (nullable LONG delta-ZigZag + UTF8 dictionary encoding + min/max zone-map predicate pushdown); Snappy/Zstd compression out of scope."

    const val MAX_ROWS = 65_536
    private const val MAGIC_LONG: Byte = 0x4C // 'L'
    private const val MAGIC_UTF8: Byte = 0x53 // 'S'

    data class LongStats(val rowCount: Int, val nullCount: Int, val min: Long?, val max: Long?) {
        fun mayMatchRange(lowInclusive: Long, highInclusive: Long): Boolean {
            val mn = min ?: return false
            val mx = max ?: return false
            return mx >= lowInclusive && mn <= highInclusive
        }
    }

    data class Utf8Stats(val rowCount: Int, val nullCount: Int, val dictSize: Int, val min: String?, val max: String?) {
        fun mayMatchPrefix(prefix: String): Boolean {
            val mn = min ?: return false
            val mx = max ?: return false
            return mx >= prefix && (mn.startsWith(prefix) || mn <= prefix || prefix <= mx)
        }
    }

    fun encodeLongColumn(values: List<Long?>): ByteArray {
        require(values.size <= MAX_ROWS) { "column exceeds $MAX_ROWS rows" }
        val nonNull = values.filterNotNull()
        val nullCount = values.size - nonNull.size
        val minV = nonNull.minOrNull()
        val maxV = nonNull.maxOrNull()

        val out = ByteArrayOutputStream()
        out.write(MAGIC_LONG.toInt())
        val hdr = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
            .putInt(values.size)
            .putInt(nullCount)
            .putLong(minV ?: 0L)
            .putLong(maxV ?: 0L)
            .array()
        out.write(hdr)
        out.write(packValidityBitmap(values.map { it != null }))

        var prev = 0L
        for (v in nonNull) {
            val delta = v - prev
            prev = v
            val zz = (delta shl 1) xor (delta shr 63)
            writeUVarLong(out, zz)
        }
        return out.toByteArray()
    }

    fun inspectLongStats(page: ByteArray): LongStats {
        require(page.size >= 25 && page[0] == MAGIC_LONG) { "invalid LONG columnar page header" }
        val bb = ByteBuffer.wrap(page, 1, 24).order(ByteOrder.BIG_ENDIAN)
        val rows = bb.int
        val nulls = bb.int
        val mn = bb.long
        val mx = bb.long
        val hasValues = rows > nulls
        return LongStats(rows, nulls, if (hasValues) mn else null, if (hasValues) mx else null)
    }

    fun decodeLongColumn(page: ByteArray): List<Long?> {
        val stats = inspectLongStats(page)
        val bitmapBytes = (stats.rowCount + 7) / 8
        var pos = 25
        require(pos + bitmapBytes <= page.size) { "truncated validity bitmap" }
        val valid = unpackValidityBitmap(page, pos, stats.rowCount)
        pos += bitmapBytes
        val out = ArrayList<Long?>(stats.rowCount)
        var prev = 0L
        for (isPresent in valid) {
            if (!isPresent) {
                out.add(null)
            } else {
                val (zz, next) = readUVarLong(page, pos)
                pos = next
                val delta = (zz ushr 1) xor -(zz and 1L)
                prev += delta
                out.add(prev)
            }
        }
        return out
    }

    fun encodeUtf8Column(values: List<String?>): ByteArray {
        require(values.size <= MAX_ROWS) { "column exceeds $MAX_ROWS rows" }
        val dictIndex = LinkedHashMap<String, Int>()
        var nullCount = 0
        var minS: String? = null
        var maxS: String? = null
        for (v in values) {
            if (v == null) {
                nullCount++
            } else {
                dictIndex.putIfAbsent(v, dictIndex.size)
                if (minS == null || v < minS) minS = v
                if (maxS == null || v > maxS) maxS = v
            }
        }
        val out = ByteArrayOutputStream()
        out.write(MAGIC_UTF8.toInt())
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putInt(values.size)
            .putInt(nullCount)
            .putInt(dictIndex.size)
            .array()
        out.write(hdr)
        writeShortStr(out, minS.orEmpty())
        writeShortStr(out, maxS.orEmpty())
        for (entry in dictIndex.keys) writeShortStr(out, entry)
        out.write(packValidityBitmap(values.map { it != null }))
        for (v in values) {
            if (v != null) writeUVarLong(out, dictIndex.getValue(v).toLong())
        }
        return out.toByteArray()
    }

    fun inspectUtf8Stats(page: ByteArray): Utf8Stats {
        require(page.size >= 13 && page[0] == MAGIC_UTF8) { "invalid UTF8 columnar page header" }
        val bb = ByteBuffer.wrap(page, 1, 12).order(ByteOrder.BIG_ENDIAN)
        val rows = bb.int
        val nulls = bb.int
        val dictSz = bb.int
        val (mn, p1) = readShortStr(page, 13)
        val (mx, _) = readShortStr(page, p1)
        val hasValues = rows > nulls
        return Utf8Stats(rows, nulls, dictSz, if (hasValues) mn else null, if (hasValues) mx else null)
    }

    fun decodeUtf8Column(page: ByteArray): List<String?> {
        require(page.size >= 13 && page[0] == MAGIC_UTF8) { "invalid UTF8 columnar page header" }
        val bb = ByteBuffer.wrap(page, 1, 12).order(ByteOrder.BIG_ENDIAN)
        val rows = bb.int
        val dictSz = bb.int.let { bb.int } // rows at 1, nulls at 5, dictSz at 9
        var pos = 13
        pos = readShortStr(page, pos).second
        pos = readShortStr(page, pos).second
        val dict = ArrayList<String>(dictSz)
        repeat(dictSz) {
            val (s, next) = readShortStr(page, pos)
            dict.add(s)
            pos = next
        }
        val bitmapBytes = (rows + 7) / 8
        val valid = unpackValidityBitmap(page, pos, rows)
        pos += bitmapBytes
        val out = ArrayList<String?>(rows)
        for (isPresent in valid) {
            if (!isPresent) {
                out.add(null)
            } else {
                val (idx, next) = readUVarLong(page, pos)
                pos = next
                out.add(dict[idx.toInt()])
            }
        }
        return out
    }

    private fun packValidityBitmap(valid: List<Boolean>): ByteArray {
        val bytes = ByteArray((valid.size + 7) / 8)
        for ((i, ok) in valid.withIndex()) {
            if (ok) bytes[i ushr 3] = (bytes[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
        }
        return bytes
    }

    private fun unpackValidityBitmap(buf: ByteArray, offset: Int, count: Int): BooleanArray {
        val out = BooleanArray(count)
        for (i in 0 until count) {
            out[i] = ((buf[offset + (i ushr 3)].toInt() ushr (i and 7)) and 1) != 0
        }
        return out
    }

    private fun writeShortStr(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        writeUVarLong(out, b.size.toLong())
        out.write(b)
    }

    private fun readShortStr(buf: ByteArray, offset: Int): Pair<String, Int> {
        val (lenL, p) = readUVarLong(buf, offset)
        val len = lenL.toInt()
        require(len >= 0 && p + len <= buf.size) { "truncated UTF8 string in columnar page" }
        return String(buf, p, len, StandardCharsets.UTF_8) to (p + len)
    }

    private fun writeUVarLong(out: ByteArrayOutputStream, v: Long) {
        var x = v
        while ((x and -0x80L) != 0L) {
            out.write(((x.toInt() and 0x7F) or 0x80))
            x = x ushr 7
        }
        out.write(x.toInt() and 0x7F)
    }

    private fun readUVarLong(buf: ByteArray, start: Int): Pair<Long, Int> {
        var res = 0L
        var shift = 0
        var p = start
        while (true) {
            require(p < buf.size && shift < 70) { "malformed varlong at $start" }
            val b = buf[p++].toInt() and 0xFF
            res = res or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) return res to p
            shift += 7
        }
    }
}
