// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Pure-JVM **Content-Defined Chunking (`CDC` Gear-Hash Rolling Fingerprint)** Delta Sync Slicer
 * for `:core:sync`, isolating byte insertions inside large `.docx` / `.mp4` / Recall archives to
 * a single variable-length chunk instead of shifting every subsequent fixed-offset block!
 */
object GearRollingChunkSlicer {
    const val SCOPE =
        "Gear-hash content-defined chunking (CDC) variable-size boundary slicer with min/avg/max chunk clamping."

    private val GEAR_TABLE: IntArray = IntArray(256) { i ->
        var x = (i + 1) * 0x9E3779B9.toInt()
        x = x xor (x ushr 16)
        x *= 0x85EBCA6B.toInt()
        x = x xor (x ushr 13)
        x
    }

    data class CdcSlice(val offset: Int, val length: Int)

    fun slice(bytes: ByteArray, minChunk: Int = 1024, maskBits: Int = 12, maxChunk: Int = 16_384): List<CdcSlice> {
        require(minChunk >= 64 && maxChunk > minChunk && maskBits in 6..20)
        if (bytes.isEmpty()) return emptyList()
        val mask = (1 shl maskBits) - 1
        val out = ArrayList<CdcSlice>()
        var chunkStart = 0
        var fp = 0
        var i = 0
        while (i < bytes.size) {
            fp = (fp shl 1) + GEAR_TABLE[bytes[i].toInt() and 0xFF]
            val curLen = i - chunkStart + 1
            if ((curLen >= minChunk && (fp and mask) == 0) || curLen >= maxChunk) {
                out.add(CdcSlice(chunkStart, curLen))
                chunkStart = i + 1
                fp = 0
            }
            i++
        }
        if (chunkStart < bytes.size) out.add(CdcSlice(chunkStart, bytes.size - chunkStart))
        return out
    }
}
