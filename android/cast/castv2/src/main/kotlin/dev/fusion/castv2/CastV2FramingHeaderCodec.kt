// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room **CastV2 TLS Framing + Protobuf Wire-Type Encoder/Decoder** (`CastMessage`:
 * `4-byte big-endian length` + `protocol_version=0`, `source_id`, `destination_id`, `namespace`,
 * `payload_type`, `payload_utf8`) for LAN receiver media session control.
 */
object CastV2FramingHeaderCodec {
    const val SCOPE =
        "CastV2 4-byte length-prefixed Protobuf wire-format CastMessage encoder and parser."

    const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val NS_MEDIA = "urn:x-cast:com.google.cast.media"

    data class CastFrame(
        val protocolVersion: Int,
        val sourceId: String,
        val destinationId: String,
        val namespaceUrn: String,
        val payloadUtf8: String,
    )

    fun encodeFrame(frame: CastFrame): ByteArray {
        val body = ByteArrayOutputStream()
        writeVarintField(body, fieldNumber = 1, value = frame.protocolVersion)
        writeLengthDelimitedString(body, fieldNumber = 2, value = frame.sourceId)
        writeLengthDelimitedString(body, fieldNumber = 3, value = frame.destinationId)
        writeLengthDelimitedString(body, fieldNumber = 4, value = frame.namespaceUrn)
        writeVarintField(body, fieldNumber = 5, value = 0) // payload_type = STRING (0)
        writeLengthDelimitedString(body, fieldNumber = 6, value = frame.payloadUtf8)
        val pb = body.toByteArray()
        require(pb.size <= 65536) { "CastV2 frame exceeds 64 KiB max packet limit" }
        return ByteBuffer.allocate(4 + pb.size).order(ByteOrder.BIG_ENDIAN).putInt(pb.size).put(pb).array()
    }

    fun decodeFrame(wire: ByteArray): CastFrame {
        require(wire.size >= 8)
        val len = ByteBuffer.wrap(wire, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        require(len == wire.size - 4) { "CastV2 length prefix $len != remaining ${wire.size - 4}" }
        var pos = 4
        var ver = 0; var src = ""; var dst = ""; var ns = ""; var utf8 = ""
        while (pos < wire.size) {
            val (tag, next1) = readVarint(wire, pos); pos = next1
            val fieldNum = tag ushr 3
            val wireType = tag and 0x07
            when (wireType) {
                0 -> {
                    val (v, next2) = readVarint(wire, pos); pos = next2
                    if (fieldNum == 1) ver = v
                }
                2 -> {
                    val (sLen, next2) = readVarint(wire, pos); pos = next2
                    require(pos + sLen <= wire.size)
                    val str = String(wire, pos, sLen, Charsets.UTF_8)
                    pos += sLen
                    when (fieldNum) {
                        2 -> src = str; 3 -> dst = str; 4 -> ns = str; 6 -> utf8 = str
                    }
                }
                else -> error("Unsupported protobuf wireType $wireType")
            }
        }
        return CastFrame(ver, src, dst, ns, utf8)
    }

    private fun writeVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Int) {
        writeRawVarint(out, (fieldNumber shl 3) or 0)
        writeRawVarint(out, value)
    }

    private fun writeLengthDelimitedString(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        val b = value.toByteArray(Charsets.UTF_8)
        writeRawVarint(out, (fieldNumber shl 3) or 2)
        writeRawVarint(out, b.size)
        out.write(b)
    }

    private fun writeRawVarint(out: ByteArrayOutputStream, vIn: Int) {
        var v = vIn
        while ((v and 0x7F.inv()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun readVarint(b: ByteArray, start: Int): Pair<Int, Int> {
        var res = 0; var shift = 0; var p = start
        while (p < b.size) {
            val byteVal = b[p++].toInt() and 0xFF
            res = res or ((byteVal and 0x7F) shl shift)
            if ((byteVal and 0x80) == 0) return res to p
            shift += 7
        }
        error("Truncated protobuf varint")
    }
}
