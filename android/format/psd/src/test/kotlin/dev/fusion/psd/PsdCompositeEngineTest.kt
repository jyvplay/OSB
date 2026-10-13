package dev.fusion.psd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests the pure decode core. No android.graphics is touched, so these run on
 * a plain JVM via `:format:psd:test`.
 */
class PsdCompositeEngineTest {

    private fun int32be(v: Int) = byteArrayOf(
        (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()
    )

    private fun psd(
        width: Int,
        height: Int,
        channels: Int,
        compression: Int,
        imageData: ByteArray,
        depth: Int = 8,
        colorMode: Int = 3,
        version: Int = 1
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("8BPS".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, version.toByte()))
        out.write(ByteArray(6)) // reserved
        out.write(byteArrayOf(0, channels.toByte()))
        out.write(int32be(height))
        out.write(int32be(width))
        out.write(byteArrayOf(0, depth.toByte()))
        out.write(byteArrayOf(0, colorMode.toByte()))
        out.write(int32be(0)) // colour mode data
        out.write(int32be(0)) // image resources
        out.write(int32be(0)) // layer & mask
        out.write(byteArrayOf(0, compression.toByte()))
        out.write(imageData)
        return out.toByteArray()
    }

    @Test
    fun decodesRawRgbPixelsExactly() {
        // 2x2, solid red: R plane 0xFF, G plane 0x00, B plane 0x00.
        val w = 2; val h = 2
        val planes = ByteArray(4) { 0xFF.toByte() } + ByteArray(4) + ByteArray(4)
        val raster = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(w, h, 3, 0, planes))
        ).getOrThrow()

        assertEquals(2, raster.width)
        assertEquals(2, raster.height)
        assertEquals(3, raster.channels)
        assertEquals(4, raster.argb.size)
        // Opaque red in ARGB_8888.
        raster.argb.forEach { assertEquals(0xFFFF0000.toInt(), it) }
    }

    @Test
    fun decodesAlphaChannelWhenPresent() {
        // 1x1 RGBA: R=0x10 G=0x20 B=0x30 A=0x40
        val data = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val raster = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(1, 1, 4, 0, data))
        ).getOrThrow()
        assertEquals(4, raster.channels)
        assertEquals(0x40102030, raster.argb[0])
    }

    @Test
    fun decodesPackBitsRleScanlines() {
        // 4x1 per channel. PackBits: -3 (0xFD) then value => repeat 4 times.
        val w = 4; val h = 1
        fun rleRun(value: Int) = byteArrayOf(0xFD.toByte(), value.toByte())
        val red = rleRun(0xFF); val green = rleRun(0x00); val blue = rleRun(0x00)

        val table = ByteArrayOutputStream()
        // 3 channels x 1 row, each scanline is 2 bytes long.
        repeat(3) { table.write(byteArrayOf(0, 2)) }

        val body = ByteArrayOutputStream()
        body.write(table.toByteArray())
        body.write(red); body.write(green); body.write(blue)

        val raster = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(w, h, 3, 1, body.toByteArray()))
        ).getOrThrow()

        assertEquals(4, raster.argb.size)
        raster.argb.forEach { assertEquals(0xFFFF0000.toInt(), it) }
    }

    @Test
    fun unpackBitsHandlesLiteralRunAndNoOp() {
        val dst = ByteArray(5)
        // 0x02 -> copy next 3 literal bytes; 0x80 (-128) -> no-op.
        val src = byteArrayOf(0x02, 0x0A, 0x0B, 0x0C, 0x80.toByte())
        val written = PsdCompositeEngine.unpackBits(src, dst, 0, 5)
        assertEquals(3, written)
        assertEquals(0x0A.toByte(), dst[0])
        assertEquals(0x0B.toByte(), dst[1])
        assertEquals(0x0C.toByte(), dst[2])
    }

    @Test
    fun rejectsWrongSignature() {
        val result = PsdCompositeEngine.decodeRaster(ByteArrayInputStream(ByteArray(40)))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("8BPS"))
    }

    @Test
    fun rejectsUnsupportedDepthWithClearMessage() {
        val result = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(1, 1, 3, 0, ByteArray(6), depth = 16))
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("8-bit"))
    }

    @Test
    fun rejectsPsbVersionTwo() {
        val result = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(1, 1, 3, 0, ByteArray(3), version = 2))
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("PSB"))
    }

    @Test
    fun rejectsZipCompression() {
        val result = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(1, 1, 3, 2, ByteArray(3)))
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("compression"))
    }

    @Test
    fun rejectsTruncatedPixelData() {
        // Declares 4x4 but supplies only a few bytes.
        val result = PsdCompositeEngine.decodeRaster(
            ByteArrayInputStream(psd(4, 4, 3, 0, ByteArray(5)))
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Truncated"))
    }
}
