package dev.fusion.castv2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtoTest {

    @Test
    fun encodeMatchesHandComputedBytes() {
        // CastMessage(sourceId="a", destinationId="b", namespace="n",
        //   payloadType=STRING, payloadUtf8="x"), protocolVersion=0.
        // field1 varint 0 -> 08 00
        // field2 "a"     -> 12 01 61
        // field3 "b"     -> 1A 01 62
        // field4 "n"     -> 22 01 6E
        // field5 varint 0 -> 28 00
        // field6 "x"     -> 32 01 78
        val msg = CastMessage(
            sourceId = "a",
            destinationId = "b",
            namespace = "n",
            payloadType = CastMessage.PAYLOAD_STRING,
            payloadUtf8 = "x"
        )
        val expected = byteArrayOf(
            0x08, 0x00,
            0x12, 0x01, 0x61,
            0x1A, 0x01, 0x62,
            0x22, 0x01, 0x6E,
            0x28, 0x00,
            0x32, 0x01, 0x78
        )
        assertArrayEquals(expected, Proto.encode(msg))
    }

    @Test
    fun roundTripPreservesAllFields() {
        val original = CastMessage(
            protocolVersion = 0,
            sourceId = "sender-0",
            destinationId = "receiver-0",
            namespace = "urn:x-cast:com.google.cast.tp.connection",
            payloadType = CastMessage.PAYLOAD_STRING,
            payloadUtf8 = """{"type":"CONNECT"}"""
        )
        val decoded = Proto.decode(Proto.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun roundTripBinaryPayload() {
        val bin = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0x7F)
        val original = CastMessage(
            sourceId = "s",
            destinationId = "d",
            namespace = "ns",
            payloadType = CastMessage.PAYLOAD_BINARY,
            payloadBinary = bin
        )
        val decoded = Proto.decode(Proto.encode(original))
        assertEquals(original, decoded)
        assertArrayEquals(bin, decoded.payloadBinary)
    }

    @Test
    fun roundTripLargeVarintProtocolVersion() {
        val msg = CastMessage(
            protocolVersion = 0,
            sourceId = "x".repeat(300), // forces multi-byte length varint
            destinationId = "y",
            namespace = "z",
            payloadType = CastMessage.PAYLOAD_STRING,
            payloadUtf8 = "payload"
        )
        assertEquals(msg, Proto.decode(Proto.encode(msg)))
    }
}
