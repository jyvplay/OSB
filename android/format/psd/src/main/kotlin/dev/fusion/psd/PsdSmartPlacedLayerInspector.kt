package dev.fusion.psd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room Adobe Photoshop (`.psd`) `8BIM` `SoLd` / `PlLd` Smart Object Placed-Layer
 * Non-Destructive Affine Quad Transform Inspector & Builder (Apache-2.0).
 */
object PsdSmartPlacedLayerInspector {
    const val SCOPE =
        "PSD 8BIM PlLd/SoLd Placed-Layer Smart Object UUID + 4-corner perspective transform quad builder & inspector"

    data class SmartLayerPlacement(
        val documentUuid: String,
        val pageNumber: Int,
        val quadCornersXy: List<Pair<Double, Double>>, // TL, TR, BR, BL (8 IEEE-754 doubles)
    ) {
        val boundingWidth: Double
            get() = quadCornersXy.maxOf { it.first } - quadCornersXy.minOf { it.first }
        val boundingHeight: Double
            get() = quadCornersXy.maxOf { it.second } - quadCornersXy.minOf { it.second }
    }

    fun encodePlLdBlock(placement: SmartLayerPlacement): ByteArray {
        require(placement.quadCornersXy.size == 4 && placement.documentUuid.isNotBlank())
        val uuidBytes = placement.documentUuid.toByteArray(StandardCharsets.US_ASCII)
        val payloadLen = 4 + 4 + 1 + uuidBytes.size + 4 + 4 + (8 * 8)
        val buf = ByteBuffer.allocate(12 + payloadLen).order(ByteOrder.BIG_ENDIAN)
        buf.put("8BIM".toByteArray(StandardCharsets.US_ASCII))
        buf.put("PlLd".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(payloadLen)
        buf.put("plcL".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(3) // PlLd version 3
        buf.put(uuidBytes.size.toByte())
        buf.put(uuidBytes)
        buf.putInt(placement.pageNumber)
        buf.putInt(1) // totalPages
        for ((x, y) in placement.quadCornersXy) {
            buf.putDouble(x)
            buf.putDouble(y)
        }
        return buf.array()
    }

    fun decodePlLdBlock(bytes: ByteArray): SmartLayerPlacement {
        require(bytes.size >= 12 + 9 + 64) { "Truncated PSD PlLd Additional Layer Info block" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val sig = ByteArray(4); bb.get(sig)
        val key = ByteArray(4); bb.get(key)
        require(String(sig, StandardCharsets.US_ASCII) == "8BIM") { "Expected 8BIM signature" }
        require(String(key, StandardCharsets.US_ASCII) == "PlLd") { "Expected PlLd key" }
        val blockLen = bb.int
        require(blockLen >= 0 && 12 + blockLen <= bytes.size) { "Invalid PlLd block length $blockLen" }
        val innerType = ByteArray(4); bb.get(innerType)
        require(String(innerType, StandardCharsets.US_ASCII) == "plcL") { "Expected plcL type" }
        val version = bb.int
        require(version == 3) { "Unsupported PlLd version $version" }
        val uuidLen = bb.get().toInt() and 0xFF
        val uuidBytes = ByteArray(uuidLen); bb.get(uuidBytes)
        val page = bb.int
        bb.int // totalPages
        val corners = List(4) { bb.double to bb.double }
        return SmartLayerPlacement(String(uuidBytes, StandardCharsets.US_ASCII), page, corners)
    }
}
