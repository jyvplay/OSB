// SPDX-License-Identifier: Apache-2.0
package dev.fusion.photos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PhotoMetadataCleanerTest {
    private fun syntheticJpegWithExifAndTrailingGarbage(): ByteArray {
        val out = ByteArrayOutputStream()
        fun seg(marker: Int, payload: ByteArray) {
            out.write(0xFF); out.write(marker)
            val len = payload.size + 2
            out.write((len shr 8) and 0xFF); out.write(len and 0xFF)
            out.write(payload)
        }
        out.write(0xFF); out.write(0xD8) // SOI
        seg(0xE1, "Exif\u0000\u0000GPS:35.7796,-78.6382".toByteArray()) // APP1 EXIF/GPS
        seg(0xED, "Photoshop 3.0 IPTC Author Secret".toByteArray()) // APP13 IPTC
        seg(0xFE, "Camera serial comment".toByteArray()) // COM
        seg(0xDB, ByteArray(65) { (it + 1).toByte() }) // DQT
        seg(0xC0, byteArrayOf(8, 0, 16, 0, 24, 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1)) // SOF0 24x16 RGB
        seg(0xC4, ByteArray(29) { 1 }) // DHT
        seg(0xDA, byteArrayOf(3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0)) // SOS header
        out.write(byteArrayOf(0xA5.toByte(), 0xFF.toByte(), 0x00, 0x3C, 0x7E)) // Byte-stuffed entropy scan
        out.write(0xFF); out.write(0xD9) // EOI
        out.write("HIDDEN_APPEND_PAYLOAD".toByteArray()) // Trailing bytes past EOI
        return out.toByteArray()
    }

    @Test
    fun stripsExifGpsIptcCommentAndTrailingBytesWhilePreservingExactEntropyScan() {
        val dirty = syntheticJpegWithExifAndTrailingGarbage()
        val result = PhotoMetadataCleaner.inspectAndCleanJpeg(dirty)
        assertEquals(24, result.width)
        assertEquals(16, result.height)
        assertEquals(3, result.components)
        assertTrue(result.removedMarkers.contains("APP1(EXIF/XMP)"))
        assertTrue(result.removedMarkers.contains("APP13"))
        assertTrue(result.removedMarkers.contains("COM"))
        assertTrue(result.removedMarkers.any { it.startsWith("TRAILING_BYTES") })
        val cleanString = String(result.bytes, Charsets.ISO_8859_1)
        assertTrue("GPS" !in cleanString && "Photoshop" !in cleanString && "HIDDEN_APPEND" !in cleanString)
        // Entropy scan between SOS..EOI is byte-for-byte identical (zero recompression loss).
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0xFF.toByte(), 0x00, 0x3C, 0x7E, 0xFF.toByte(), 0xD9.toByte()),
            PhotoMetadataCleaner.extractEntropyScan(result.bytes)
        )
        // Corrupt header fails closed.
        assertThrows(IllegalArgumentException::class.java) {
            PhotoMetadataCleaner.inspectAndCleanJpeg(byteArrayOf(0, 1, 2, 3, 4))
        }
    }
}
