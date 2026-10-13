package dev.fusion.airplay

import java.util.Base64

/**
 * Pure protocol helper for RAOP/AirPlay 1 audio announce.
 * No sockets, pairing or streaming are implemented in this build.
 */
object RaopSdp {
    fun buildAlac(aesKey: ByteArray, iv: ByteArray): String = buildString {
        appendLine("v=0")
        appendLine("o=iTunes 0 0 IN IP4 127.0.0.1")
        appendLine("s=iTunes")
        appendLine("c=IN IP4 127.0.0.1")
        appendLine("t=0 0")
        appendLine("m=audio 0 RTP/AVP 96")
        appendLine("a=rtpmap:96 AppleLossless")
        appendLine("a=fmtp:96 352 0 16 40 10 14 2 255 0 0 44100")
        appendLine("a=rsaaeskey:${Base64.getEncoder().encodeToString(aesKey)}")
        appendLine("a=aesiv:${Base64.getEncoder().encodeToString(iv)}")
    }
}
