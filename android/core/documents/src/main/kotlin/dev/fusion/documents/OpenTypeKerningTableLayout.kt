// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room OpenType `kern` Format-0 binary table codec + standard `fi`/`fl`/`ffi`/`ffl`
 * ligature substitution + positioned glyph run calculator (`ISO/IEC 14496-22` OpenType spec).
 */
object OpenTypeKerningTableLayout {
    const val SCOPE =
        "Pure-JVM OpenType Format-0 binary kern table encoder/decoder, binary-search glyph-pair kerning, and standard Latin ligature layout."

    data class KernPair(val leftGlyph: Int, val rightGlyph: Int, val valueFUnits: Int) {
        val key32: Long get() = ((leftGlyph.toLong() and 0xFFFFL) shl 16) or (rightGlyph.toLong() and 0xFFFFL)
    }

    data class PositionedGlyph(
        val glyphId: Int,
        val textCluster: String,
        val xOffsetEm: Double,
        val advanceEm: Double,
    )

    data class PositionedRun(
        val glyphs: List<PositionedGlyph>,
        val totalAdvanceEm: Double,
        val appliedKernPairs: Int,
        val appliedLigatures: Int,
    )

    private val LIGATURES = listOf(
        "ffi" to 0xFB03,
        "ffl" to 0xFB04,
        "fi" to 0xFB01,
        "fl" to 0xFB02,
    )

    fun encodeKernTable(pairs: List<KernPair>): ByteArray {
        val sorted = pairs.distinctBy { it.key32 }.sortedBy { it.key32 }
        require(sorted.size <= 8192) { "too many kern pairs (${sorted.size})" }
        val nPairs = sorted.size
        var entrySelector = 0
        var highestPow2 = 1
        while (highestPow2 * 2 <= maxOf(1, nPairs)) {
            highestPow2 *= 2
            entrySelector++
        }
        val searchRange = if (nPairs == 0) 0 else highestPow2 * 6
        val rangeShift = if (nPairs == 0) 0 else (nPairs * 6) - searchRange
        val subtableLen = 6 + 8 + nPairs * 6
        val out = ByteArrayOutputStream(4 + subtableLen)
        val buf = ByteBuffer.allocate(4 + subtableLen).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0) // table version 0
        buf.putShort(1) // nTables = 1
        buf.putShort(0) // subtable version 0
        buf.putShort(subtableLen.toShort())
        buf.putShort(0x0001) // horizontal kerning format 0
        buf.putShort(nPairs.toShort())
        buf.putShort(searchRange.toShort())
        buf.putShort(entrySelector.toShort())
        buf.putShort(rangeShift.toShort())
        for (kp in sorted) {
            buf.putShort(kp.leftGlyph.toShort())
            buf.putShort(kp.rightGlyph.toShort())
            buf.putShort(kp.valueFUnits.coerceIn(-32768, 32767).toShort())
        }
        out.write(buf.array())
        return out.toByteArray()
    }

    fun decodeKernTable(bytes: ByteArray): List<KernPair> {
        require(bytes.size >= 18) { "truncated OpenType kern table (${bytes.size} bytes)" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buf.short.toInt() and 0xFFFF
        val nTables = buf.short.toInt() and 0xFFFF
        require(version == 0 && nTables >= 1) { "unsupported kern header v=$version nTables=$nTables" }
        buf.short // subVersion
        val subLen = buf.short.toInt() and 0xFFFF
        val coverage = buf.short.toInt() and 0xFFFF
        require((coverage ushr 8) == 0 && (coverage and 0x01) == 1) { "expected format-0 horizontal kern subtable" }
        val nPairs = buf.short.toInt() and 0xFFFF
        buf.short; buf.short; buf.short // searchRange, entrySelector, rangeShift
        require(subLen >= 14 + nPairs * 6 && bytes.size >= 4 + 14 + nPairs * 6) { "truncated kern pair array" }
        return List(nPairs) {
            val l = buf.short.toInt() and 0xFFFF
            val r = buf.short.toInt() and 0xFFFF
            val v = buf.short.toInt()
            KernPair(l, r, v)
        }
    }

    fun layoutText(
        text: String,
        kernTableBytes: ByteArray,
        unitsPerEm: Int = 1000,
        enableLigatures: Boolean = true,
    ): PositionedRun {
        require(unitsPerEm in 64..16384) { "unitsPerEm out of range" }
        val pairs = decodeKernTable(kernTableBytes).associateBy({ it.key32 }, { it.valueFUnits })
        val rawTokens = ArrayList<Pair<Int, String>>()
        var i = 0
        var ligCount = 0
        while (i < text.length) {
            var matched = false
            if (enableLigatures) {
                for ((pat, code) in LIGATURES) {
                    if (text.startsWith(pat, i)) {
                        rawTokens.add(code to pat)
                        i += pat.length
                        ligCount++
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                val cp = text.codePointAt(i)
                val s = String(Character.toChars(cp))
                rawTokens.add(cp to s)
                i += Character.charCount(cp)
            }
        }

        var cursorEm = 0.0
        var kernHits = 0
        val positioned = ArrayList<PositionedGlyph>(rawTokens.size)
        for (idx in rawTokens.indices) {
            val (gid, cluster) = rawTokens[idx]
            val baseFUnits = glyphBaseAdvanceFUnits(gid, unitsPerEm)
            val kernFUnits = if (idx + 1 < rawTokens.size) {
                val key = ((gid.toLong() and 0xFFFFL) shl 16) or (rawTokens[idx + 1].first.toLong() and 0xFFFFL)
                val kv = pairs[key] ?: 0
                if (kv != 0) kernHits++
                kv
            } else 0
            val advEm = (baseFUnits + kernFUnits).toDouble() / unitsPerEm.toDouble()
            positioned.add(PositionedGlyph(gid, cluster, cursorEm, advEm))
            cursorEm += advEm
        }
        return PositionedRun(positioned, cursorEm, kernHits, ligCount)
    }

    private fun glyphBaseAdvanceFUnits(gid: Int, upem: Int): Int = when (gid) {
        ' '.code -> (upem * 0.28).toInt()
        'i'.code, 'l'.code, '!'.code, '.'.code, ','.code -> (upem * 0.30).toInt()
        'm'.code, 'w'.code, 'M'.code, 'W'.code -> (upem * 0.82).toInt()
        0xFB01, 0xFB02 -> (upem * 0.56).toInt()
        0xFB03, 0xFB04 -> (upem * 0.84).toInt()
        in 'A'.code..'Z'.code -> (upem * 0.66).toInt()
        else -> (upem * 0.54).toInt()
    }
}
