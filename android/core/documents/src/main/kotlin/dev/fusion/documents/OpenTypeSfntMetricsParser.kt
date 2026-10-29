// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room ISO/IEC 14496-22 OpenType / TrueType `sfnt` binary font metrics parser & synthetic builder.
 *
 * Parses Offset Table (`0x00010000` / `'OTTO'`), `head` (`unitsPerEm`), `hhea` (`ascent`, `descent`,
 * `lineGap`, `numberOfHMetrics`), `maxp` (`numGlyphs`), `cmap` Format 4 (BMP segmented) & Format 12
 * (UCS-4 segmented), `hmtx` (`advanceWidth`, `lsb`), and `kern` Format 0 kerning pairs.
 *
 * Adversarial invariants enforced (`SixLabors/Fonts#565`, `genoffice#1771`):
 * 1. Bounds-checks `16 + 8 * segCount <= subtableLength` before reading `cmap` Format 4 segment arrays.
 * 2. Applies `(glyphId + idDelta[i]) & 0xFFFF` even in the `idRangeOffset[i] != 0` branch when `glyphId != 0`,
 *    and keeps `0` (`.notdef`) when `glyphId == 0`.
 * 3. Clamps `hmtx` lookups for `glyphId in numberOfHMetrics until numGlyphs` to the final `advanceWidth`
 *    and returns `0` cleanly for `glyphId >= numGlyphs`.
 */
object OpenTypeSfntMetricsParser {
    private const val SFNT_TRUETYPE = 0x00010000
    private const val SFNT_CFF_OTTO = 0x4F54544F

    data class FontMetricsFace(
        val sfntVersion: Int,
        val unitsPerEm: Int,
        val ascent: Int,
        val descent: Int,
        val lineGap: Int,
        val numGlyphs: Int,
        val numberOfHMetrics: Int,
        private val cmapFormat4Slice: ByteArray?,
        private val cmapFormat12Groups: List<IntArray>,
        private val advanceWidths: IntArray,
        private val leftSideBearings: IntArray,
        private val kernPairs: Map<Long, Int>,
    ) {
        fun glyphIdOf(codePoint: Int): Int {
            if (codePoint < 0 || codePoint > 0x10FFFF) return 0
            if (cmapFormat12Groups.isNotEmpty()) {
                var lo = 0; var hi = cmapFormat12Groups.lastIndex
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    val g = cmapFormat12Groups[mid]
                    when {
                        codePoint < g[0] -> hi = mid - 1
                        codePoint > g[1] -> lo = mid + 1
                        else -> {
                            val gid = g[2] + (codePoint - g[0])
                            return if (gid in 1 until numGlyphs) gid else 0
                        }
                    }
                }
            }
            if (codePoint <= 0xFFFF && cmapFormat4Slice != null) {
                val gid = lookupFormat4(cmapFormat4Slice, codePoint)
                return if (gid in 1 until numGlyphs) gid else 0
            }
            return 0
        }

        fun advanceWidthUnits(glyphId: Int): Int {
            if (glyphId < 0 || glyphId >= numGlyphs || advanceWidths.isEmpty()) return 0
            return if (glyphId < advanceWidths.size) advanceWidths[glyphId] else advanceWidths.last()
        }

        fun kernUnits(leftGlyphId: Int, rightGlyphId: Int): Int {
            val key = ((leftGlyphId.toLong() and 0xFFFFL) shl 16) or (rightGlyphId.toLong() and 0xFFFFL)
            return kernPairs[key] ?: 0
        }

        fun measureTextPoints(text: String, fontSizePt: Double): Double {
            require(fontSizePt > 0.0) { "fontSizePt must be positive" }
            var totalUnits = 0L
            var prevGid = -1
            var i = 0
            while (i < text.length) {
                val cp = Character.codePointAt(text, i)
                i += Character.charCount(cp)
                val gid = glyphIdOf(cp)
                totalUnits += advanceWidthUnits(gid)
                if (prevGid >= 0) totalUnits += kernUnits(prevGid, gid)
                prevGid = gid
            }
            return totalUnits.toDouble() * fontSizePt / unitsPerEm.toDouble()
        }
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun i16(buf: ByteArray, off: Int): Int = u16(buf, off).toShort().toInt()

    private fun u32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFFL) shl 24) or
            ((buf[off + 1].toLong() and 0xFFL) shl 16) or
            ((buf[off + 2].toLong() and 0xFFL) shl 8) or
            (buf[off + 3].toLong() and 0xFFL)

    private fun lookupFormat4(sub: ByteArray, cp: Int): Int {
        if (sub.size < 16 || u16(sub, 0) != 4) return 0
        val declaredLen = u16(sub, 2).coerceAtMost(sub.size)
        val segCountX2 = u16(sub, 6)
        if (segCountX2 < 2 || segCountX2 % 2 != 0) return 0
        val segCount = segCountX2 / 2
        val headerAndArrays = 16 + 8 * segCount
        // Strict truncation guard (genoffice#1771): all 4 parallel arrays + reservedPad must fit
        if (headerAndArrays > declaredLen) return 0

        val endCodeBase = 14
        val startCodeBase = 16 + 2 * segCount
        val idDeltaBase = 16 + 4 * segCount
        val idRangeOffsetBase = 16 + 6 * segCount

        var lo = 0; var hi = segCount - 1
        var seg = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val endC = u16(sub, endCodeBase + mid * 2)
            if (cp <= endC) {
                seg = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        if (seg < 0) return 0
        val startC = u16(sub, startCodeBase + seg * 2)
        if (cp < startC || cp == 0xFFFF) return 0
        val delta = i16(sub, idDeltaBase + seg * 2)
        val rangeOff = u16(sub, idRangeOffsetBase + seg * 2)
        if (rangeOff == 0) {
            return (cp + delta) and 0xFFFF
        }
        val roFieldAddr = idRangeOffsetBase + seg * 2
        val glyphIndexAddr = roFieldAddr + rangeOff + 2 * (cp - startC)
        if (glyphIndexAddr < headerAndArrays || glyphIndexAddr + 2 > declaredLen) return 0
        val rawGlyph = u16(sub, glyphIndexAddr)
        // SixLabors/Fonts#565: glyph 0 stays 0 (.notdef); non-zero adds idDelta mod 65536
        return if (rawGlyph == 0) 0 else (rawGlyph + delta) and 0xFFFF
    }

    fun parse(sfntBytes: ByteArray): FontMetricsFace {
        require(sfntBytes.size >= 12) { "Truncated sfnt header (${sfntBytes.size} bytes)" }
        val version = u32(sfntBytes, 0).toInt()
        require(version == SFNT_TRUETYPE || version == SFNT_CFF_OTTO) {
            "Unsupported sfnt magic 0x${version.toUInt().toString(16)}"
        }
        val numTables = u16(sfntBytes, 4)
        require(numTables in 1..128 && 12 + numTables * 16 <= sfntBytes.size) { "Invalid sfnt numTables=$numTables" }

        val tables = HashMap<String, Pair<Int, Int>>()
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(sfntBytes, rec, 4, Charsets.US_ASCII)
            val off = u32(sfntBytes, rec + 8)
            val len = u32(sfntBytes, rec + 12)
            require(off >= 12L && len >= 0L && off + len <= sfntBytes.size.toLong()) {
                "Out-of-bounds table '$tag' offset=$off len=$len"
            }
            tables[tag] = off.toInt() to len.toInt()
        }

        val (headOff, headLen) = tables["head"] ?: error("Missing required 'head' table")
        require(headLen >= 36) { "Truncated 'head' table" }
        val unitsPerEm = u16(sfntBytes, headOff + 18)
        require(unitsPerEm in 16..16384) { "Out-of-spec unitsPerEm=$unitsPerEm" }

        val (hheaOff, hheaLen) = tables["hhea"] ?: error("Missing required 'hhea' table")
        require(hheaLen >= 36) { "Truncated 'hhea' table" }
        val ascent = i16(sfntBytes, hheaOff + 4)
        val descent = i16(sfntBytes, hheaOff + 6)
        val lineGap = i16(sfntBytes, hheaOff + 8)
        val numHMetrics = u16(sfntBytes, hheaOff + 34)

        val (maxpOff, maxpLen) = tables["maxp"] ?: error("Missing required 'maxp' table")
        require(maxpLen >= 6) { "Truncated 'maxp' table" }
        val numGlyphs = u16(sfntBytes, maxpOff + 4)
        require(numGlyphs >= 1 && numHMetrics in 1..numGlyphs) { "Invalid glyph/hMetric counts" }

        val (hmtxOff, hmtxLen) = tables["hmtx"] ?: error("Missing required 'hmtx' table")
        require(hmtxLen >= numHMetrics * 4) { "Truncated 'hmtx' table" }
        val adv = IntArray(numHMetrics)
        val lsb = IntArray(numGlyphs)
        for (i in 0 until numHMetrics) {
            adv[i] = u16(sfntBytes, hmtxOff + i * 4)
            lsb[i] = i16(sfntBytes, hmtxOff + i * 4 + 2)
        }
        val extraLsbBase = hmtxOff + numHMetrics * 4
        for (i in numHMetrics until numGlyphs) {
            val pos = extraLsbBase + (i - numHMetrics) * 2
            if (pos + 2 <= hmtxOff + hmtxLen) lsb[i] = i16(sfntBytes, pos)
        }

        var fmt4Slice: ByteArray? = null
        val fmt12Groups = ArrayList<IntArray>()
        tables["cmap"]?.let { (cmapOff, cmapLen) ->
            require(cmapLen >= 4) { "Truncated 'cmap'" }
            val subCount = u16(sfntBytes, cmapOff + 2)
            require(4 + subCount * 8 <= cmapLen) { "Truncated 'cmap' encoding directory" }
            for (i in 0 until subCount) {
                val rec = cmapOff + 4 + i * 8
                val subRel = u32(sfntBytes, rec + 4).toInt()
                require(subRel in 0 until cmapLen) { "Invalid cmap subtable offset" }
                val absSub = cmapOff + subRel
                val fmt = u16(sfntBytes, absSub)
                if (fmt == 4 && fmt4Slice == null) {
                    val subLen = u16(sfntBytes, absSub + 2)
                    require(subRel + subLen <= cmapLen) { "Truncated cmap format 4 subtable" }
                    fmt4Slice = sfntBytes.copyOfRange(absSub, absSub + subLen)
                } else if (fmt == 12 && fmt12Groups.isEmpty() && subRel + 16 <= cmapLen) {
                    val nGroups = u32(sfntBytes, absSub + 12).toInt()
                    require(nGroups in 0..65536 && subRel + 16 + nGroups * 12 <= cmapLen) { "Truncated cmap format 12" }
                    for (g in 0 until nGroups) {
                        val gp = absSub + 16 + g * 12
                        fmt12Groups.add(intArrayOf(u32(sfntBytes, gp).toInt(), u32(sfntBytes, gp + 4).toInt(), u32(sfntBytes, gp + 8).toInt()))
                    }
                }
            }
        }

        val kerns = HashMap<Long, Int>()
        tables["kern"]?.let { (kernOff, kernLen) ->
            if (kernLen >= 18 && u16(sfntBytes, kernOff) == 0 && u16(sfntBytes, kernOff + 2) >= 1) {
                val coverage = u16(sfntBytes, kernOff + 8)
                if ((coverage ushr 8) == 0) {
                    val nPairs = u16(sfntBytes, kernOff + 10)
                    if (18 + nPairs * 6 <= kernLen) {
                        for (p in 0 until nPairs) {
                            val base = kernOff + 18 + p * 6
                            val l = u16(sfntBytes, base)
                            val r = u16(sfntBytes, base + 2)
                            val v = i16(sfntBytes, base + 4)
                            kerns[((l.toLong() and 0xFFFFL) shl 16) or (r.toLong() and 0xFFFFL)] = v
                        }
                    }
                }
            }
        }

        return FontMetricsFace(
            sfntVersion = version,
            unitsPerEm = unitsPerEm,
            ascent = ascent,
            descent = descent,
            lineGap = lineGap,
            numGlyphs = numGlyphs,
            numberOfHMetrics = numHMetrics,
            cmapFormat4Slice = fmt4Slice,
            cmapFormat12Groups = fmt12Groups,
            advanceWidths = adv,
            leftSideBearings = lsb,
            kernPairs = kerns,
        )
    }

    /** Builds a clean-room valid binary TrueType (`0x00010000`) font for deterministic typography tests & artifacts. */
    fun buildSyntheticProportionalFont(unitsPerEm: Int = 1000): ByteArray {
        // Glyph 0=.notdef(500), 1=' '(250), 2..95=ASCII 0x21..0x7E
        val numGlyphs = 96
        val advances = IntArray(numGlyphs) { gid ->
            when {
                gid == 0 -> 500
                gid == 1 -> 250
                else -> {
                    val ch = (0x20 + (gid - 1)).toChar()
                    when (ch) {
                        'i', 'l', '.', ',', ':', ';', '!', '|' -> 280
                        'm', 'w', 'M', 'W' -> 820
                        in 'A'..'Z' -> 640
                        else -> 520
                    }
                }
            }
        }

        val head = ByteBuffer.allocate(54).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000); putInt(0x00010000); putInt(0); putInt(0x5F0F3CF5)
            putShort(0); putShort(unitsPerEm.toShort())
            putLong(0L); putLong(0L)
            putShort(0); putShort((-200).toShort()); putShort(900); putShort(800)
            putShort(0); putShort(8); putShort(2); putShort(0); putShort(0)
        }.array()

        val hhea = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000); putShort(800); putShort((-200).toShort()); putShort(100)
            putShort(850); putShort(0); putShort(0); putShort(850)
            putShort(1); putShort(0); putShort(0)
            repeat(4) { putShort(0) }
            putShort(0); putShort(numGlyphs.toShort())
        }.array()

        val maxp = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00005000); putShort(numGlyphs.toShort())
        }.array()

        val hmtx = ByteBuffer.allocate(numGlyphs * 4).order(ByteOrder.BIG_ENDIAN).apply {
            for (g in 0 until numGlyphs) {
                putShort(advances[g].toShort())
                putShort(20)
            }
        }.array()

        // cmap format 4 with 2 segments: [0x0020..0x007E -> delta = -31], [0xFFFF..0xFFFF -> delta = 1]
        val fmt4 = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(4); putShort(32); putShort(0); putShort(4) // segCountX2 = 4
            putShort(4); putShort(1); putShort(0)
            putShort(0x007E); putShort(0xFFFF.toShort()) // endCode
            putShort(0) // reservedPad
            putShort(0x0020); putShort(0xFFFF.toShort()) // startCode
            putShort((-31).toShort()); putShort(1) // idDelta: 0x0020 + (-31) = 1
            putShort(0); putShort(0) // idRangeOffset
        }.array()
        val cmap = ByteBuffer.allocate(12 + fmt4.size).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1)
            putShort(3); putShort(1); putInt(12)
            put(fmt4)
        }.array()

        // kern format 0 with 2 pairs: ('A'=gid 34, 'V'=gid 55 -> -80), ('T'=gid 53, 'o'=gid 80 -> -60)
        val kern = ByteBuffer.allocate(30).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1)
            putShort(0); putShort(26); putShort(0x0001)
            putShort(2); putShort(12); putShort(1); putShort(0)
            putShort(34); putShort(55); putShort((-80).toShort())
            putShort(53); putShort(80); putShort((-60).toShort())
        }.array()

        val sortedTables = listOf(
            "cmap" to cmap,
            "head" to head,
            "hhea" to hhea,
            "hmtx" to hmtx,
            "kern" to kern,
            "maxp" to maxp,
        )
        val numT = sortedTables.size
        val dirSize = 12 + numT * 16
        val bodyOut = ByteArrayOutputStream()
        val records = ArrayList<Triple<String, Int, ByteArray>>()
        var curOffset = dirSize
        for ((tag, data) in sortedTables) {
            records.add(Triple(tag, curOffset, data))
            bodyOut.write(data)
            val pad = (4 - (data.size % 4)) % 4
            repeat(pad) { bodyOut.write(0) }
            curOffset += data.size + pad
        }

        val header = ByteBuffer.allocate(dirSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(SFNT_TRUETYPE)
            putShort(numT.toShort()); putShort(64); putShort(2); putShort(32)
            for ((tag, off, data) in records) {
                put(tag.toByteArray(Charsets.US_ASCII))
                putInt(tableChecksum(data))
                putInt(off)
                putInt(data.size)
            }
        }.array()
        return header + bodyOut.toByteArray()
    }

    private fun tableChecksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i < data.size) {
            var word = 0L
            for (b in 0..3) {
                word = (word shl 8) or (if (i + b < data.size) (data[i + b].toLong() and 0xFFL) else 0L)
            }
            sum = (sum + word) and 0xFFFF_FFFFL
            i += 4
        }
        return sum.toInt()
    }
}
