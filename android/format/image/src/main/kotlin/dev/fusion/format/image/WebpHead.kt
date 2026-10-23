// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/**
 * WebP container header parser pinned to RFC 9649 (November 2024) and the
 * libwebp container spec:
 *
 * - File layout: "RIFF" + u32le size + "WEBP" + chunks. The RIFF size
 *   covers everything after those first 8 bytes.
 * - Each chunk: 4-byte FourCC + u32le payload size + payload, padded to
 *   even length.
 * - Extended format first chunk is 'VP8X' with a 10-byte payload:
 *   byte 0 flag bits (MSB first) Rsv(2) I L E X A R — i.e. ICC 0x20,
 *   Alpha 0x10, Exif 0x08, XMP 0x04, Animation 0x02 (matching libwebp's
 *   published flag values); 24 reserved bits; then Canvas Width Minus One
 *   (u24le) and Canvas Height Minus One (u24le). Actual canvas is the
 *   stored value + 1, and "the product of Canvas Width and Canvas Height
 *   MUST be at most 2^32 - 1".
 * - Simple-format files start with 'VP8 ' (lossy) or 'VP8L' (lossless);
 *   this kernel reports their kind without parsing the bitstream.
 * - Reader MUSTs are followed as reader rules: reserved VP8X bits are
 *   read and ignored, not refused.
 *
 * Pure JVM logic over a byte array; no decoding, rendering, or pixels.
 */
object WebpHead {
    enum class Kind { LOSSY, LOSSLESS, EXTENDED }

    data class Info(
        val kind: Kind,
        val canvasWidth: Long?,
        val canvasHeight: Long?,
        val hasIcc: Boolean,
        val hasAlpha: Boolean,
        val hasExif: Boolean,
        val hasXmp: Boolean,
        val isAnimated: Boolean,
        val riffSize: Long,
    )

    const val FLAG_ICC = 0x20
    const val FLAG_ALPHA = 0x10
    const val FLAG_EXIF = 0x08
    const val FLAG_XMP = 0x04
    const val FLAG_ANIM = 0x02

    fun parse(bytes: ByteArray): Info {
        require(bytes.size >= 20) { "too short for a WebP header: ${bytes.size}" }
        require(fourCc(bytes, 0) == "RIFF") { "missing RIFF tag" }
        val riffSize = le32(bytes, 4)
        require(riffSize == bytes.size.toLong() - 8) { "RIFF size ${riffSize} != file size - 8 (${bytes.size - 8})" }
        require(fourCc(bytes, 8) == "WEBP") { "missing WEBP form type" }
        val chunk = fourCc(bytes, 12)
        val payload = le32(bytes, 16)
        require(20 + payload <= bytes.size) { "first chunk payload overruns file" }
        return when (chunk) {
            "VP8 " -> Info(Kind.LOSSY, null, null, false, false, false, false, false, riffSize)
            "VP8L" -> Info(Kind.LOSSLESS, null, null, false, false, false, false, false, riffSize)
            "VP8X" -> {
                require(payload == 10L) { "VP8X payload must be 10 bytes, got $payload" }
                val flags = bytes[20].toInt() and 0xFF
                val w = le24(bytes, 24) + 1
                val h = le24(bytes, 27) + 1
                require(w * h <= 0xFFFFFFFFL) { "canvas width*height exceeds 2^32-1" }
                Info(
                    Kind.EXTENDED, w, h,
                    (flags and FLAG_ICC) != 0,
                    (flags and FLAG_ALPHA) != 0,
                    (flags and FLAG_EXIF) != 0,
                    (flags and FLAG_XMP) != 0,
                    (flags and FLAG_ANIM) != 0,
                    riffSize,
                )
            }
            else -> throw IllegalArgumentException("first chunk must be VP8 /VP8L/VP8X, got '$chunk'")
        }
    }

    private fun fourCc(b: ByteArray, off: Int): String {
        val sb = StringBuilder(4)
        for (i in 0 until 4) {
            val v = b[off + i].toInt() and 0xFF
            require(v in 0x20..0x7E) { "non-ASCII FourCC byte" }
            sb.append(v.toChar())
        }
        return sb.toString()
    }

    private fun le24(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or ((b[off + 2].toLong() and 0xFF) shl 16)

    private fun le32(b: ByteArray, off: Int): Long = le24(b, off) or ((b[off + 3].toLong() and 0xFF) shl 24)
}
