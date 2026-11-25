// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Production-grade CastV2 4-byte big-endian length-prefixed framing codec.
 */
class CastV2FrameRouter private constructor() {
    companion object {
        const val MAX_FRAME_BYTES = 65_536

        fun encodeFrame(payload: ByteArray): ByteArray {
            require(payload.size <= MAX_FRAME_BYTES) { "payload exceeds MAX_FRAME_BYTES" }
            val out = ByteArray(4 + payload.size)
            val len = payload.size
            out[0] = ((len ushr 24) and 0xFF).toByte()
            out[1] = ((len ushr 16) and 0xFF).toByte()
            out[2] = ((len ushr 8) and 0xFF).toByte()
            out[3] = (len and 0xFF).toByte()
            System.arraycopy(payload, 0, out, 4, payload.size)
            return out
        }

        fun decodeFrameLength(header4: ByteArray): Int {
            require(header4.size == 4) { "header must be 4 bytes" }
            val len = ((header4[0].toInt() and 0xFF) shl 24) or
                ((header4[1].toInt() and 0xFF) shl 16) or
                ((header4[2].toInt() and 0xFF) shl 8) or
                (header4[3].toInt() and 0xFF)
            require(len in 0..MAX_FRAME_BYTES) { "frame length out of bounds: $len" }
            return len
        }
    }
}
