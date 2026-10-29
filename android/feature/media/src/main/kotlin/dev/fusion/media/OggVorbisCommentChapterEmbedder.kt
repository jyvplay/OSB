// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Xiph Vorbis Comment + Ogg Opus `OpusTags` chapter marker embedder (`CHAPTER001=00:00:00.000`,
 * `CHAPTER001NAME=...`) for **Recall** podcast and concert music recordings.
 */
object OggVorbisCommentChapterEmbedder {
    const val SCOPE =
        "Xiph Vorbis Comment and OpusTags little-endian UTF-8 tag/chapter block encoder and parser (CHAPTERxxx=HH:MM:SS.mmm)."

    data class ChapterMark(val index: Int, val startMillis: Long, val title: String)

    fun buildOpusTagsPayload(vendor: String, comments: Map<String, String>, chapters: List<ChapterMark>): ByteArray {
        val entries = ArrayList<String>()
        for ((k, v) in comments) {
            val cleanKey = k.uppercase().filter { it.code in 0x20..0x7D && it != '=' }
            require(cleanKey.isNotEmpty()) { "Invalid Vorbis comment key '$k'" }
            entries.add("$cleanKey=$v")
        }
        for (ch in chapters.sortedBy { it.index }) {
            require(ch.index in 1..999 && ch.startMillis >= 0L)
            val ms = (ch.startMillis % 1000L).toInt()
            val totSec = ch.startMillis / 1000L
            val s = (totSec % 60L).toInt()
            val m = ((totSec / 60L) % 60L).toInt()
            val h = (totSec / 3600L).toInt()
            entries.add("CHAPTER%03d=%02d:%02d:%02d.%03d".format(ch.index, h, m, s, ms))
            entries.add("CHAPTER%03dNAME=%s".format(ch.index, ch.title))
        }

        val out = ByteArrayOutputStream()
        out.write("OpusTags".toByteArray(Charsets.US_ASCII))
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        out.write(le32(vendorBytes.size))
        out.write(vendorBytes)
        out.write(le32(entries.size))
        for (e in entries) {
            val eb = e.toByteArray(Charsets.UTF_8)
            out.write(le32(eb.size))
            out.write(eb)
        }
        return out.toByteArray()
    }

    fun parseOpusTagsPayload(bytes: ByteArray): Pair<String, Map<String, String>> {
        require(bytes.size >= 16 && String(bytes, 0, 8, Charsets.US_ASCII) == "OpusTags") { "Missing OpusTags signature" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8)
        val vLen = buf.int
        require(vLen >= 0 && buf.remaining() >= vLen + 4)
        val vBytes = ByteArray(vLen); buf.get(vBytes)
        val count = buf.int
        require(count in 0..4096)
        val map = LinkedHashMap<String, String>()
        repeat(count) {
            val cLen = buf.int
            require(cLen >= 0 && buf.remaining() >= cLen)
            val cb = ByteArray(cLen); buf.get(cb)
            val str = String(cb, Charsets.UTF_8)
            val eq = str.indexOf('=')
            if (eq > 0) map[str.substring(0, eq)] = str.substring(eq + 1)
        }
        return String(vBytes, Charsets.UTF_8) to map
    }

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
}
