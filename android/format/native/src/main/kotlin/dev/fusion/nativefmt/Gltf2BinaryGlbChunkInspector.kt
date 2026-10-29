// SPDX-License-Identifier: Apache-2.0
package dev.fusion.nativefmt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Khronos **glTF 2.0** Binary (`.glb` — ISO/IEC 12113:2022) container header (`magic = 0x46546C67`
 * `"glTF"`, `version = 2`, `length`) + `JSON` (`0x4E4F534A`) and `BIN\0` (`0x004E4942`) 4-byte-aligned
 * chunk builder and verifier.
 */
object Gltf2BinaryGlbChunkInspector {
    const val SCOPE =
        "Khronos glTF 2.0 / ISO 12113 .glb binary 12-byte header and 4-byte-aligned JSON + BIN chunk builder and verifier."

    private const val MAGIC_GLTF = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    data class GlbInspection(
        val version: Int,
        val totalLengthBytes: Int,
        val jsonText: String,
        val binaryBufferLength: Int,
    )

    fun buildGlb(jsonString: String, binPayload: ByteArray): ByteArray {
        val rawJson = jsonString.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (rawJson.size % 4)) % 4
        val binPad = (4 - (binPayload.size % 4)) % 4
        val jsonChunkLen = rawJson.size + jsonPad
        val binChunkLen = binPayload.size + binPad
        val totalLen = 12 + 8 + jsonChunkLen + (if (binPayload.isNotEmpty()) 8 + binChunkLen else 0)

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC_GLTF)
        buf.putInt(2)
        buf.putInt(totalLen)
        // Chunk 0: JSON padded with 0x20 spaces per glTF 2.0 §4.4
        buf.putInt(jsonChunkLen)
        buf.putInt(CHUNK_JSON)
        buf.put(rawJson)
        repeat(jsonPad) { buf.put(0x20.toByte()) }
        // Chunk 1: BIN padded with 0x00 per glTF 2.0 §4.4
        if (binPayload.isNotEmpty()) {
            buf.putInt(binChunkLen)
            buf.putInt(CHUNK_BIN)
            buf.put(binPayload)
            repeat(binPad) { buf.put(0x00.toByte()) }
        }
        return buf.array()
    }

    fun inspectGlb(glbBytes: ByteArray): GlbInspection {
        require(glbBytes.size >= 20) { "Truncated GLB header" }
        val buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        val version = buf.int
        val totalLen = buf.int
        require(magic == MAGIC_GLTF && version == 2 && totalLen == glbBytes.size) {
            "Invalid GLB header magic=0x${magic.toUInt().toString(16)} version=$version len=$totalLen"
        }
        val jLen = buf.int
        val jType = buf.int
        require(jType == CHUNK_JSON && jLen >= 0 && jLen % 4 == 0 && buf.remaining() >= jLen) {
            "First GLB chunk must be 4-byte-aligned JSON"
        }
        val jBytes = ByteArray(jLen); buf.get(jBytes)
        val jsonStr = String(jBytes, Charsets.UTF_8).trimEnd(' ')
        var binLen = 0
        if (buf.hasRemaining()) {
            require(buf.remaining() >= 8)
            val bLen = buf.int
            val bType = buf.int
            require(bType == CHUNK_BIN && bLen % 4 == 0 && buf.remaining() == bLen) {
                "Second GLB chunk must be 4-byte-aligned BIN"
            }
            binLen = bLen
        }
        return GlbInspection(version, totalLen, jsonStr, binLen)
    }
}
