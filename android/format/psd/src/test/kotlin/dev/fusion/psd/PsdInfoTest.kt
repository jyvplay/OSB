package dev.fusion.psd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PsdInfoTest {
    @Test fun parsesBasicHeader() {
        val h = byteArrayOf(
            '8'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte(),
            0, 1, 0,0,0,0,0,0,
            0, 3,
            0,0,0x02,0x00.toByte(),
            0,0,0x03,0x20,
            0, 8,
            0, 3
        )
        val psd = PsdInfo.read(ByteArrayInputStream(h)).getOrThrow()
        assertEquals(3, psd.channels)
        assertEquals(512, psd.height)
        assertEquals(800, psd.width)
    }
    @Test fun rejectsWrongMagic() {
        val bad = ByteArray(26)
        val res = PsdInfo.read(ByteArrayInputStream(bad))
        assertTrue(res.isFailure)
    }
}
