package dev.fusion.testing

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Shared, dependency-free test fixtures.
 *
 * These build real byte payloads (a valid ZIP, a PSD header) so format-module
 * tests exercise actual parsers instead of hand-typed hex blobs.
 */
object Fixtures {

    /** A valid ZIP archive containing one text file. */
    fun zipWithTextFile(name: String = "a.txt", content: String = "hello fusion"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * A minimal but structurally valid PSD header (8BPS, version 1, RGB, 8-bit)
     * followed by empty Color Mode / Image Resources / Layer blocks and a
     * compression marker. Not a renderable image — for header-parser tests.
     */
    fun psdHeaderBytes(width: Int, height: Int, channels: Int = 3): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("8BPS".toByteArray(Charsets.US_ASCII)) // signature
        out.write(byteArrayOf(0, 1)) // version 1
        out.write(ByteArray(6)) // reserved
        out.write(byteArrayOf(0, channels.toByte())) // channels
        out.write(int32be(height))
        out.write(int32be(width))
        out.write(byteArrayOf(0, 8)) // depth 8
        out.write(byteArrayOf(0, 3)) // color mode RGB
        out.write(int32be(0)) // color mode data length
        out.write(int32be(0)) // image resources length
        out.write(int32be(0)) // layer & mask length
        out.write(byteArrayOf(0, 0)) // compression: raw
        return out.toByteArray()
    }

    private fun int32be(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )
}

/** A manually-advanced clock for time-based logic in tests. */
class FakeClock(var nowMillis: Long = 0L) {
    fun advance(ms: Long) { nowMillis += ms }
    fun now(): Long = nowMillis
}
