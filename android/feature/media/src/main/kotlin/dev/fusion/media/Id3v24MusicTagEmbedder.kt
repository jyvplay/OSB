// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Pure-JVM **ID3v2.4 (`TIT2`, `TPE1`, `TALB`, `WOAF`, `TDRC`) Synchsafe Binary Tag Writer & Reader**
 * for Recall's automatic music archiver (`:feature:media`), embedding clean source metadata into
 * recorded `.mp3` / audio streams without corrupting MPEG audio frames!
 */
object Id3v24MusicTagEmbedder {
    const val SCOPE =
        "ID3v2.4 28-bit synchsafe header + UTF-8 TIT2/TPE1/TALB/WOAF/TDRC tag serializer and parser for Recall archived music tracks."

    data class MusicTrackMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val sourcePageUrl: String,
        val recordYear: String = "2026",
    )

    fun prependId3v24Tag(rawAudioBytes: ByteArray, meta: MusicTrackMetadata): ByteArray {
        val frames = ByteArrayOutputBuffer()
        writeTextFrame(frames, "TIT2", meta.title)
        writeTextFrame(frames, "TPE1", meta.artist)
        writeTextFrame(frames, "TALB", meta.album)
        writeTextFrame(frames, "TDRC", meta.recordYear)
        writeUrlFrame(frames, "WOAF", meta.sourcePageUrl)

        val payload = frames.toByteArray()
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 4 // ID3v2.4
        header[4] = 0
        header[5] = 0
        writeSynchsafe28(header, 6, payload.size)
        return header + payload + rawAudioBytes
    }

    fun parseId3v24Tag(taggedBytes: ByteArray): Pair<MusicTrackMetadata?, Int> {
        if (taggedBytes.size < 10 ||
            taggedBytes[0] != 'I'.code.toByte() ||
            taggedBytes[1] != 'D'.code.toByte() ||
            taggedBytes[2] != '3'.code.toByte()
        ) return null to 0

        val tagLen = readSynchsafe28(taggedBytes, 6)
        val end = (10 + tagLen).coerceAtMost(taggedBytes.size)
        var pos = 10
        val fields = HashMap<String, String>()
        while (pos + 10 <= end) {
            val id = String(taggedBytes, pos, 4, StandardCharsets.US_ASCII)
            if (id.any { !it.isLetterOrDigit() }) break
            val frameLen = if (taggedBytes[3].toInt() == 4) readSynchsafe28(taggedBytes, pos + 4)
            else ByteBuffer.wrap(taggedBytes, pos + 4, 4).int
            pos += 10
            if (frameLen <= 0 || pos + frameLen > end) break
            val value = if (id.startsWith("W")) {
                String(taggedBytes, pos, frameLen, StandardCharsets.ISO_8859_1)
            } else {
                val enc = taggedBytes[pos].toInt() and 0xFF
                val cs = if (enc == 3) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
                String(taggedBytes, pos + 1, frameLen - 1, cs).trimEnd('\u0000')
            }
            fields[id] = value
            pos += frameLen
        }
        val meta = MusicTrackMetadata(
            title = fields["TIT2"].orEmpty(),
            artist = fields["TPE1"].orEmpty(),
            album = fields["TALB"].orEmpty(),
            sourcePageUrl = fields["WOAF"].orEmpty(),
            recordYear = fields["TDRC"] ?: "2026",
        )
        return meta to (10 + tagLen)
    }

    private fun writeTextFrame(out: ByteArrayOutputBuffer, id: String, value: String) {
        if (value.isBlank()) return
        val utf8 = value.toByteArray(StandardCharsets.UTF_8)
        val bodyLen = 1 + utf8.size
        out.writeAscii(id)
        out.writeSynchsafe(bodyLen)
        out.writeByte(0); out.writeByte(0) // flags
        out.writeByte(3) // 0x03 = UTF-8 in ID3v2.4
        out.writeBytes(utf8)
    }

    private fun writeUrlFrame(out: ByteArrayOutputBuffer, id: String, url: String) {
        if (url.isBlank()) return
        val ascii = url.toByteArray(StandardCharsets.ISO_8859_1)
        out.writeAscii(id)
        out.writeSynchsafe(ascii.size)
        out.writeByte(0); out.writeByte(0)
        out.writeBytes(ascii)
    }

    private fun writeSynchsafe28(dst: ByteArray, off: Int, v: Int) {
        dst[off] = ((v ushr 21) and 0x7F).toByte()
        dst[off + 1] = ((v ushr 14) and 0x7F).toByte()
        dst[off + 2] = ((v ushr 7) and 0x7F).toByte()
        dst[off + 3] = (v and 0x7F).toByte()
    }

    private fun readSynchsafe28(src: ByteArray, off: Int): Int =
        ((src[off].toInt() and 0x7F) shl 21) or
            ((src[off + 1].toInt() and 0x7F) shl 14) or
            ((src[off + 2].toInt() and 0x7F) shl 7) or
            (src[off + 3].toInt() and 0x7F)

    private class ByteArrayOutputBuffer {
        private val buf = java.io.ByteArrayOutputStream()
        fun writeByte(v: Int) = buf.write(v and 0xFF)
        fun writeBytes(b: ByteArray) = buf.write(b, 0, b.size)
        fun writeAscii(s: String) = writeBytes(s.toByteArray(StandardCharsets.US_ASCII))
        fun writeSynchsafe(v: Int) {
            val tmp = ByteArray(4)
            writeSynchsafe28(tmp, 0, v)
            writeBytes(tmp)
        }
        fun toByteArray(): ByteArray = buf.toByteArray()
    }
}
