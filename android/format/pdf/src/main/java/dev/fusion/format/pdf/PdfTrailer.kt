// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/** Parsed trailer: version, object count, root reference, xref offset. */
data class PdfTrailerInfo(val version: String, val size: Int, val rootRef: String, val startxref: Long)

/** One cross-reference table entry. */
data class XrefEntry(val offset: Long, val generation: Int, val free: Boolean)

/**
 * Minimal PDF trailer/xref reader: `%PDF-1.m` header, last `startxref`
 * offset, `trailer<<...>>` Size/Root subset, and classic `xref` table
 * entries. No object parsing, no streams, no rendering — locating the
 * root is where this stops. Caps files at 64 MiB, Size at 100k.
 */
object PdfTrailer {
    const val MAX_BYTES = 64 * 1024 * 1024
    const val MAX_SIZE = 100_000
    private val VERSION = Regex("%PDF-1\\.[0-7]")
    private val SIZE = Regex("/Size\\s+(\\d+)")
    private val ROOT = Regex("/Root\\s+(\\d+\\s+\\d+\\s+R)")
    private val ENTRY = Regex("(\\d{10}) (\\d{5}) ([fn]) \n")

    fun parse(bytes: ByteArray): PdfTrailerInfo {
        require(bytes.size <= MAX_BYTES) { "file over $MAX_BYTES bytes" }
        val text = bytes.toString(Charsets.US_ASCII)
        val version = VERSION.find(text.substring(0, minOf(32, text.length)))?.value
            ?: throw IllegalArgumentException("bad %PDF- header")
        require(text.trimEnd().endsWith("%%EOF")) { "missing %%EOF" }
        val sx = text.lastIndexOf("startxref")
        require(sx >= 0) { "missing startxref" }
        val startxref = text.substring(sx + 9).trim().substringBefore('\n').trim().toLongOrNull()
            ?: throw IllegalArgumentException("bad startxref offset")
        require(startxref in 0 until bytes.size) { "startxref outside file" }
        val trailerAt = text.lastIndexOf("trailer")
        require(trailerAt in 0 until sx || text.indexOf("trailer", sx) >= 0) { "missing trailer" }
        val dict = text.substring(text.lastIndexOf("trailer"))
        val size = SIZE.find(dict)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("bad /Size")
        require(size in 1..MAX_SIZE) { "/Size $size outside 1..$MAX_SIZE" }
        val root = ROOT.find(dict)?.groupValues?.get(1)?.split(Regex("\\s+"))?.joinToString(" ")
            ?: throw IllegalArgumentException("bad /Root")
        return PdfTrailerInfo(version, size, root, startxref)
    }

    /** Classic `xref` table entries starting at [startxref] for [size] objects. */
    fun xrefEntries(bytes: ByteArray, startxref: Long, size: Int): List<XrefEntry> {
        require(bytes.size <= MAX_BYTES) { "file over $MAX_BYTES bytes" }
        require(size in 1..MAX_SIZE) { "size $size outside 1..$MAX_SIZE" }
        require(startxref in 0 until bytes.size) { "startxref outside file" }
        val text = bytes.toString(Charsets.US_ASCII)
        var i = startxref.toInt()
        require(text.startsWith("xref", i)) { "no xref at $startxref" }
        i += 4
        val out = mutableListOf<XrefEntry>()
        while (out.size < size) {
            while (i < text.length && (text[i] == '\n' || text[i] == '\r')) i++
            val head = text.substring(i).substringBefore('\n')
            val (first, count) = head.trim().split(Regex("\\s+")).map {
                it.toIntOrNull() ?: throw IllegalArgumentException("bad subsection $head")
            }.let { require(it.size == 2) { "bad subsection $head" }; it[0] to it[1] }
            require(first >= 0 && count > 0 && out.size + count <= size) { "subsection over $size" }
            i += head.length + 1
            repeat(count) {
                val m = ENTRY.matchAt(text, i) ?: throw IllegalArgumentException("bad entry at $i")
                out += XrefEntry(m.groupValues[1].toLong(), m.groupValues[2].toInt(), m.groupValues[3] == "f")
                i += 20
            }
        }
        return out
    }
}
