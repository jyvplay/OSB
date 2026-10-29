// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.io.ByteArrayOutputStream

/**
 * Clean-room unsigned LEB128 (`uvarint64`), signed ZigZag (`svarint64`), and delta-varint
 * compact integer codec (`VarIntZigZag`).
 *
 * Powers `:core:files` compact local inverted-index posting lists and snapshot manifests:
 * - Strictly rejects overlong (`> 10` bytes) or 64-bit overflow (`10th` byte `> 0x01`) varints
 *   to prevent parser wrap-around bugs (`CVE-2021-3121`-class protobuf varint overflow defense!).
 */
object VarIntZigZag {
    const val SCOPE =
        "Unsigned LEB128 uvarint64 + signed ZigZag svarint64 + delta-encoded LongArray posting codec with strict 10-byte 64-bit overflow refusal; SIMD StreamVByte out of scope."

    data class DecodedULong(val value: Long, val bytesRead: Int)

    fun zigZagEncode(signed: Long): Long = (signed shl 1) xor (signed shr 63)

    fun zigZagDecode(encodedUnsigned: Long): Long = (encodedUnsigned ushr 1) xor -(encodedUnsigned and 1L)

    fun encodeUVarInt(unsignedBits: Long): ByteArray {
        val out = ByteArray(10)
        var v = unsignedBits
        var i = 0
        while ((v and -0x80L) != 0L) {
            out[i++] = ((v.toInt() and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out[i++] = (v.toInt() and 0x7F).toByte()
        return out.copyOf(i)
    }

    fun decodeUVarInt(buf: ByteArray, offset: Int = 0): DecodedULong {
        var result = 0L
        var shift = 0
        var idx = offset
        while (shift < 64) {
            require(idx < buf.size) { "Truncated LEB128 varint at offset $offset" }
            val b = buf[idx++].toInt() and 0xFF
            if (shift == 63 && b > 0x01) {
                throw IllegalArgumentException("64-bit LEB128 varint overflow in 10th byte (0x${b.toString(16)})")
            }
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return DecodedULong(result, idx - offset)
            }
            shift += 7
        }
        throw IllegalArgumentException("Overlong LEB128 varint (> 10 bytes)")
    }

    fun encodeSigned(value: Long): ByteArray = encodeUVarInt(zigZagEncode(value))

    fun decodeSigned(buf: ByteArray, offset: Int = 0): DecodedULong {
        val u = decodeUVarInt(buf, offset)
        return DecodedULong(zigZagDecode(u.value), u.bytesRead)
    }

    fun encodeDeltaSequence(values: LongArray): ByteArray {
        val baos = ByteArrayOutputStream(values.size * 2 + 4)
        baos.write(encodeUVarInt(values.size.toLong()))
        var prev = 0L
        for (v in values) {
            val delta = v - prev
            baos.write(encodeSigned(delta))
            prev = v
        }
        return baos.toByteArray()
    }

    fun decodeDeltaSequence(bytes: ByteArray): LongArray {
        val hdr = decodeUVarInt(bytes, 0)
        require(hdr.value in 0L..1_000_000L) { "Delta sequence count out of bounds: ${hdr.value}" }
        val count = hdr.value.toInt()
        val out = LongArray(count)
        var cursor = hdr.bytesRead
        var prev = 0L
        for (i in 0 until count) {
            val d = decodeSigned(bytes, cursor)
            cursor += d.bytesRead
            prev += d.value
            out[i] = prev
        }
        require(cursor == bytes.size) { "Trailing bytes after delta sequence" }
        return out
    }
}
