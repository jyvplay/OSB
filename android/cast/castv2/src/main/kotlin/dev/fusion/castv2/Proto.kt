package dev.fusion.castv2

import java.io.ByteArrayOutputStream

/**
 * Minimal Protocol Buffers codec for the castv2 [CastMessage].
 * Hand-rolled so the module stays dependency-free (no protobuf runtime, no
 * codegen). Pure JVM — fully unit-testable.
 *
 * Wire schema (cast_channel.proto):
 *   message CastMessage {
 *     required ProtocolVersion protocol_version = 1; // enum, varint
 *     required string source_id       = 2;
 *     required string destination_id  = 3;
 *     required string namespace       = 4;
 *     required PayloadType payload_type = 5; // enum, varint: STRING=0, BINARY=1
 *     optional string payload_utf8    = 6;
 *     optional bytes  payload_binary  = 7;
 *   }
 */
data class CastMessage(
    val protocolVersion: Int = 0,
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payloadType: Int, // 0 = STRING, 1 = BINARY
    val payloadUtf8: String? = null,
    val payloadBinary: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastMessage) return false
        return protocolVersion == other.protocolVersion &&
            sourceId == other.sourceId &&
            destinationId == other.destinationId &&
            namespace == other.namespace &&
            payloadType == other.payloadType &&
            payloadUtf8 == other.payloadUtf8 &&
            payloadBinary.contentEquals(other.payloadBinary)
    }

    override fun hashCode(): Int {
        var r = protocolVersion
        r = 31 * r + sourceId.hashCode()
        r = 31 * r + destinationId.hashCode()
        r = 31 * r + namespace.hashCode()
        r = 31 * r + payloadType
        r = 31 * r + (payloadUtf8?.hashCode() ?: 0)
        r = 31 * r + (payloadBinary?.contentHashCode() ?: 0)
        return r
    }

    companion object {
        const val PAYLOAD_STRING = 0
        const val PAYLOAD_BINARY = 1
    }
}

object Proto {
    // protobuf wire types
    private const val WT_VARINT = 0
    private const val WT_64BIT = 1
    private const val WT_LEN = 2
    private const val WT_32BIT = 5

    fun encode(msg: CastMessage): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarintField(out, 1, msg.protocolVersion)
        writeStringField(out, 2, msg.sourceId)
        writeStringField(out, 3, msg.destinationId)
        writeStringField(out, 4, msg.namespace)
        writeVarintField(out, 5, msg.payloadType)
        if (msg.payloadUtf8 != null) writeStringField(out, 6, msg.payloadUtf8)
        if (msg.payloadBinary != null) writeBytesField(out, 7, msg.payloadBinary)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): CastMessage {
        val c = Cursor(bytes)
        var protocolVersion = 0
        var sourceId = ""
        var destinationId = ""
        var namespace = ""
        var payloadType = CastMessage.PAYLOAD_STRING
        var payloadUtf8: String? = null
        var payloadBinary: ByteArray? = null

        while (!c.atEnd()) {
            val tag = c.readVarint().toInt()
            val fieldNumber = tag ushr 3
            when (tag and 0x7) {
                WT_VARINT -> {
                    val v = c.readVarint().toInt()
                    when (fieldNumber) {
                        1 -> protocolVersion = v
                        5 -> payloadType = v
                        else -> { /* ignore unknown varint field */ }
                    }
                }
                WT_LEN -> {
                    val length = c.readVarint().toInt()
                    val data = c.readBytes(length)
                    when (fieldNumber) {
                        2 -> sourceId = String(data, Charsets.UTF_8)
                        3 -> destinationId = String(data, Charsets.UTF_8)
                        4 -> namespace = String(data, Charsets.UTF_8)
                        6 -> payloadUtf8 = String(data, Charsets.UTF_8)
                        7 -> payloadBinary = data
                        else -> { /* ignore unknown length-delimited field */ }
                    }
                }
                WT_64BIT -> c.skip(8)
                WT_32BIT -> c.skip(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type ${tag and 0x7}")
            }
        }
        return CastMessage(protocolVersion, sourceId, destinationId, namespace, payloadType, payloadUtf8, payloadBinary)
    }

    private fun writeTag(out: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    private fun writeVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Int) {
        writeTag(out, fieldNumber, WT_VARINT)
        writeVarint(out, value.toLong())
    }

    private fun writeStringField(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        writeBytesField(out, fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytesField(out: ByteArrayOutputStream, fieldNumber: Int, value: ByteArray) {
        writeTag(out, fieldNumber, WT_LEN)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    private class Cursor(val bytes: ByteArray) {
        var pos = 0
        fun atEnd(): Boolean = pos >= bytes.size
        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= bytes.size) throw IllegalArgumentException("Truncated varint")
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift >= 70) throw IllegalArgumentException("Varint too long")
            }
        }

        fun readBytes(length: Int): ByteArray {
            if (pos + length > bytes.size) throw IllegalArgumentException("Truncated length-delimited field")
            val data = bytes.copyOfRange(pos, pos + length)
            pos += length
            return data
        }

        fun skip(n: Int) {
            if (pos + n > bytes.size) throw IllegalArgumentException("Truncated skip")
            pos += n
        }
    }
}
