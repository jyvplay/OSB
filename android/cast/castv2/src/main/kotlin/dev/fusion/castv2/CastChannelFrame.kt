// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room CastV2 4-byte big-endian length-prefixed protobuf `CastMessage` wire encoder/decoder (`CastChannelFrame`).
 *
 * Wire schema (`cast_channel.proto` UTF-8 control message subset):
 * - Field 1 (`tag 0x08`): `protocol_version` varint (`0 = CASTV2_1_0`)
 * - Field 2 (`tag 0x12`): `source_id` UTF-8 string
 * - Field 3 (`tag 0x1A`): `destination_id` UTF-8 string
 * - Field 4 (`tag 0x22`): `namespace` UTF-8 string
 * - Field 5 (`tag 0x28`): `payload_type` varint (`0 = STRING`)
 * - Field 6 (`tag 0x32`): `payload_utf8` UTF-8 string
 */
object CastChannelFrame {
    const val SCOPE =
        "CastV2 4-byte BE length-prefixed protobuf CastMessage encoder/decoder (fields 1..6, 64 KiB cap); TLS client-cert auth challenge out of scope."

    const val MAX_FRAME_BYTES = 64 * 1024

    data class Message(
        val sourceId: String,
        val destinationId: String,
        val namespace: String,
        val payloadUtf8: String,
        val protocolVersion: Int = 0,
    )

    fun encodeFrame(msg: Message): ByteArray {
        val body = ByteArrayOutputStream()
        writeVarintField(body, 1, msg.protocolVersion)
        writeStringField(body, 2, msg.sourceId)
        writeStringField(body, 3, msg.destinationId)
        writeStringField(body, 4, msg.namespace)
        writeVarintField(body, 5, 0) // payload_type = STRING
        writeStringField(body, 6, msg.payloadUtf8)
        val pb = body.toByteArray()
        require(pb.size <= MAX_FRAME_BYTES) { "CastMessage exceeds 64 KiB cap" }
        val out = ByteArray(4 + pb.size)
        ByteBuffer.wrap(out, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(pb.size)
        System.arraycopy(pb, 0, out, 4, pb.size)
        return out
    }

    fun decodeFrame(frame: ByteArray): Message {
        require(frame.size >= 4) { "Truncated CastV2 length header" }
        val len = ByteBuffer.wrap(frame, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        require(len in 1..MAX_FRAME_BYTES && 4 + len == frame.size) { "Invalid CastV2 frame length: $len" }
        var i = 4
        var ver = 0
        var src = ""
        var dst = ""
        var ns = ""
        var payload = ""
        while (i < frame.size) {
            val (tag, nextI) = readVarint(frame, i); i = nextI
            val fieldNum = tag ushr 3
            val wireType = tag and 0x07
            when (wireType) {
                0 -> {
                    val (v, ni) = readVarint(frame, i); i = ni
                    if (fieldNum == 1) ver = v
                }
                2 -> {
                    val (sLen, ni) = readVarint(frame, i); i = ni
                    require(sLen >= 0 && i + sLen <= frame.size) { "Truncated CastV2 string field" }
                    val str = String(frame, i, sLen, StandardCharsets.UTF_8)
                    i += sLen
                    when (fieldNum) {
                        2 -> src = str
                        3 -> dst = str
                        4 -> ns = str
                        6 -> payload = str
                    }
                }
                else -> throw IllegalArgumentException("Unsupported CastV2 protobuf wireType: $wireType")
            }
        }
        return Message(src, dst, ns, payload, ver)
    }

    private fun writeVarintField(out: ByteArrayOutputStream, fieldNum: Int, value: Int) {
        writeRawVarint(out, (fieldNum shl 3) or 0)
        writeRawVarint(out, value)
    }

    private fun writeStringField(out: ByteArrayOutputStream, fieldNum: Int, s: String) {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        writeRawVarint(out, (fieldNum shl 3) or 2)
        writeRawVarint(out, b.size)
        out.write(b)
    }

    private fun writeRawVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while ((v and -0x80) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun readVarint(b: ByteArray, start: Int): Pair<Int, Int> {
        var res = 0
        var shift = 0
        var idx = start
        while (shift < 32) {
            require(idx < b.size) { "Truncated protobuf varint" }
            val byteVal = b[idx++].toInt() and 0xFF
            res = res or ((byteVal and 0x7F) shl shift)
            if ((byteVal and 0x80) == 0) return res to idx
            shift += 7
        }
        throw IllegalArgumentException("Overlong 32-bit varint")
    }
}
