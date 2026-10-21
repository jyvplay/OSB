// SPDX-License-Identifier: Apache-2.0
package dev.fusion.photos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PhotosStudioKernelTest {
    @Test
    fun stripsExifApp1WithoutRecompressingEntropyScanStream() {
        val out = ByteArrayOutputStream()
        fun seg(marker: Int, body: ByteArray) {
            out.write(0xFF); out.write(marker)
            val len = body.size + 2
            out.write((len shr 8) and 0xFF); out.write(len and 0xFF)
            out.write(body)
        }
        out.write(0xFF); out.write(0xD8)
        seg(0xE1, "Exif\u0000\u0000GPSSecret".toByteArray(StandardCharsets.US_ASCII))
        seg(0xDB, ByteArray(65) { 2 })
        seg(0xC0, byteArrayOf(8, 0, 8, 0, 8, 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1))
        seg(0xC4, ByteArray(29) { 1 })
        seg(0xDA, byteArrayOf(3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0))
        out.write(byteArrayOf(0x11, 0x22, 0xFF.toByte(), 0x00, 0x33, 0xFF.toByte(), 0xD9.toByte()))

        val res = PhotoMetadataCleaner.inspectAndCleanJpeg(out.toByteArray())
        assertTrue(res.removedMarkers.any { it.startsWith("APP1") })
        assertFalse(String(res.bytes, StandardCharsets.ISO_8859_1).contains("GPSSecret"))
    }
}
