// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * RFC 1950 zlib header reader.
 *
 * Sits next to [StrictZip] (which inflates raw deflate streams with
 * `Inflater(true)`, i.e. *without* a zlib wrapper): nothing validates the two
 * header bytes CMF/FLG that frame a zlib stream — compression method and window
 * size (CMF), the FCHECK integrity bits (FLG), the preset-dictionary flag, and
 * the compressor's level hint. Only deflate (CM = 8) with a window up to 32 KiB
 * (CINFO ≤ 7) is accepted; a set FDICT bit is refused rather than skipped,
 * because the 4 dictionary-id bytes that follow would silently shift the
 * deflate stream start for any caller that assumes a 2-byte header. FCHECK —
 * `(CMF·256 + FLG) mod 31 == 0` — is enforced so single-bit header corruption
 * fails here instead of inside the inflator.
 *
 * Plain Kotlin (ByteArray in, data out), JVM unit-testable.
 */
object ZlibHead {

    /** Parsed zlib header: window-size exponent, level hint, header length. */
    data class Header(val cinfo: Int, val flevel: Int, val headerLength: Int)

    /**
     * Parses the zlib header at the start of [bytes].
     *
     * @throws IllegalArgumentException on short input, a non-deflate method,
     * an oversized window, a failed FCHECK, or a preset dictionary.
     */
    fun parse(bytes: ByteArray): Header {
        require(bytes.size >= 2) { "short header" }
        val cmf = bytes[0].toInt() and 0xFF
        val flg = bytes[1].toInt() and 0xFF
        require(cmf * 256 + flg > 0) { "zero header" }
        require((cmf * 256 + flg) % 31 == 0) { "FCHECK failed" }
        require(cmf and 0x0F == 8) { "not deflate" }
        val cinfo = (cmf shr 4) and 0x0F
        require(cinfo <= 7) { "window too large" }
        require(flg and 0x20 == 0) { "preset dictionary unsupported" }
        return Header(cinfo, (flg shr 6) and 0x03, 2)
    }
}
