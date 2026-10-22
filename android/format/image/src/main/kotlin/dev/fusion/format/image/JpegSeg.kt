// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/**
 * JPEG marker walker: structure plus frame dimensions without decoding pixels.
 *
 * Sits next to [PngMetadata] (PNG chunk walk): nothing reads a JPEG's segment
 * list or its Start-Of-Frame dimensions. The walk starts at SOI, skips every
 * length-bearing segment (APPn, COM, DQT, DHT, DRI, SOFn, …) by its declared
 * length, tolerates standalone RSTn/DNL markers, and stops at SOS — everything
 * after Start-Of-Scan is entropy-coded data the walker must not interpret — or
 * at EOI. Baseline/extended/progressive SOF markers (C0/C1/C2) additionally
 * yield height, width, and component count; other SOF types (differential,
 * arithmetic, lossless C3) are recorded as segments but refused as dimension
 * sources, because their sample layout needs a real decoder. Truncated data,
 * a missing SOI, and segment lengths under 2 are refused.
 *
 * Plain Kotlin (ByteArray in, data out), JVM unit-testable.
 */
object JpegSeg {

    /** One marker segment: marker byte, file offset of the FF prefix, payload length. */
    data class Segment(val marker: Int, val offset: Int, val length: Int)

    /** Walk result: ordered segments plus frame geometry when an SOF0/1/2 was seen. */
    data class Info(
        val segments: List<Segment>,
        val width: Int?,
        val height: Int?,
        val components: Int?,
        val sofType: Int?,
    )

    /**
     * Walks the marker structure of [bytes].
     *
     * @throws IllegalArgumentException on a missing SOI, truncated data, or a
     * corrupt segment length.
     */
    fun walk(bytes: ByteArray): Info {
        require(bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) { "no SOI" }
        val segs = mutableListOf<Segment>()
        var width: Int? = null
        var height: Int? = null
        var comps: Int? = null
        var sof: Int? = null
        var i = 2
        var done = false
        while (i < bytes.size) {
            require(bytes[i] == 0xFF.toByte()) { "expected marker at $i" }
            var j = i + 1
            while (j < bytes.size && bytes[j] == 0xFF.toByte()) j++ // fill bytes
            require(j < bytes.size) { "truncated marker" }
            val marker = bytes[j].toInt() and 0xFF
            require(marker != 0x00 && marker != 0xFF) { "bad marker" }
            if (marker == 0xD9) { // EOI
                segs += Segment(marker, i, 0)
                done = true
                break
            }
            if (marker == 0xDA) { // SOS: entropy follows; stop without interpreting it.
                require(j + 3 < bytes.size) { "truncated SOS" }
                val len = u16(bytes, j + 1)
                require(len >= 2 && j + 1 + len <= bytes.size) { "bad SOS length" }
                segs += Segment(marker, i, len)
                done = true
                break
            }
            if (marker in 0xD0..0xD7 || marker == 0x01) { // RSTn, DNL: standalone.
                segs += Segment(marker, i, 0)
                i = j + 1
                continue
            }
            require(j + 2 < bytes.size) { "truncated length" }
            val len = u16(bytes, j + 1)
            require(len >= 2) { "bad length" }
            require(j + 1 + len <= bytes.size) { "segment overruns input" }
            segs += Segment(marker, i, len)
            if (marker in 0xC0..0xC2 && sof == null) {
                require(len >= 8) { "truncated SOF" }
                height = u16(bytes, j + 4)
                width = u16(bytes, j + 6)
                comps = bytes[j + 8].toInt() and 0xFF
                require(height!! > 0 && width!! > 0 && comps!! in 1..4) { "bad frame" }
                sof = marker
            }
            i = j + 1 + len
        }
        require(done) { "truncated" }
        return Info(segs, width, height, comps, sof)
    }

    private fun u16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}
