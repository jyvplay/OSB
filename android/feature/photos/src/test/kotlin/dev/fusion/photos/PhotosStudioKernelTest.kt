// SPDX-License-Identifier: Apache-2.0
package dev.fusion.photos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PhotosStudioKernelTest {
    @Test
    fun stripsExifApp1WithoutRecompressingEntropyScanStream() {
        val app1 = "Exif\u0000\u0000GPSSecret".toByteArray(StandardCharsets.US_ASCII)
        val lenHi = ((app1.size + 2) shr 8).toByte()
        val lenLo = ((app1.size + 2) and 0xFF).toByte()
        val sos = byteArrayOf(0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x11, 0x22, 0x33)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), lenHi, lenLo) +
            app1 + byteArrayOf(0xFF.toByte(), 0xDA.toByte()) + sos + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val res = PhotoMetadataCleaner.inspectAndCleanJpeg(jpeg)
        assertTrue(res.removedMarkers.any { it.startsWith("APP1") })
        assertFalse(String(res.bytes, StandardCharsets.ISO_8859_1).contains("GPSSecret"))
    }
}
