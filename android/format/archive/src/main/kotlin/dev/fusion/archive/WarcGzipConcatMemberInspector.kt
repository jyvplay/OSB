// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * RFC 1952 multi-member concatenated `.warc.gz` builder and per-record random-access byte-offset
 * inspector (ISO 28500 Annex D recommendation: one independent GZIP member per WARC record so
 * `RecallCdxjReplayIndexCodec` can seek directly to `(offset, length)` without decompressing the
 * whole archive!).
 */
object WarcGzipConcatMemberInspector {
    const val SCOPE =
        "RFC 1952 / ISO 28500 Annex D per-record concatenated GZIP member (.warc.gz) writer and random-access offset decompressor."

    data class GzipMemberSpan(
        val index: Int,
        val compressedOffset: Int,
        val compressedLength: Int,
        val uncompressedLength: Int,
    )

    fun compressPerRecordMembers(records: List<ByteArray>): Pair<ByteArray, List<GzipMemberSpan>> {
        require(records.isNotEmpty())
        val concat = ByteArrayOutputStream()
        val spans = ArrayList<GzipMemberSpan>(records.size)
        for ((idx, rec) in records.withIndex()) {
            val memberOut = ByteArrayOutputStream()
            GZIPOutputStream(memberOut).use { it.write(rec) }
            val comp = memberOut.toByteArray()
            val off = concat.size()
            concat.write(comp)
            spans.add(GzipMemberSpan(idx, off, comp.size, rec.size))
        }
        return concat.toByteArray() to spans
    }

    fun decompressSingleMemberAt(warcGzBytes: ByteArray, offset: Int, length: Int): ByteArray {
        require(offset >= 0 && length >= 18 && offset + length <= warcGzBytes.size)
        require((warcGzBytes[offset].toInt() and 0xFF) == 0x1F && (warcGzBytes[offset + 1].toInt() and 0xFF) == 0x8B) {
            "Slice at $offset does not start with RFC 1952 GZIP magic 0x1F8B"
        }
        return GZIPInputStream(ByteArrayInputStream(warcGzBytes, offset, length)).use { it.readBytes() }
    }
}
