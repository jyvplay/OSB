package dev.fusion.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class FixturesTest {

    @Test
    fun zipFixtureIsReadableByJdk() {
        val bytes = Fixtures.zipWithTextFile("note.txt", "payload")
        val file = File.createTempFile("fixture", ".zip").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("note.txt")
            assertNotNull(entry)
            assertEquals("payload", zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun psdHeaderFixtureHasValidSignatureAndDimensions() {
        val bytes = Fixtures.psdHeaderBytes(width = 800, height = 600)
        assertEquals("8BPS", String(bytes, 0, 4, Charsets.US_ASCII))

        fun int32At(offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

        // PSD header layout: height at byte 14, width at byte 18 (big-endian).
        assertEquals(600, int32At(14))
        assertEquals(800, int32At(18))
    }

    @Test
    fun fakeClockAdvances() {
        val clock = FakeClock(1_000L)
        clock.advance(500L)
        assertEquals(1_500L, clock.now())
    }
}
