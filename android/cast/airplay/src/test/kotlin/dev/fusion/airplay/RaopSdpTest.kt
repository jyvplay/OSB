package dev.fusion.airplay

import org.junit.Assert.assertTrue
import org.junit.Test

class RaopSdpTest {
    @Test fun containsMandatoryLines() {
        val sdp = RaopSdp.buildAlac(ByteArray(16) { 1 }, ByteArray(16) { 2 })
        assertTrue(sdp.contains("AppleLossless"))
        assertTrue(sdp.contains("a=fmtp:96 352"))
        assertTrue(sdp.contains("a=rsaaeskey:"))
        assertTrue(sdp.contains("a=aesiv:"))
    }
}
