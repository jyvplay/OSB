// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * ID3v2 tag-header reader (structure only, no frames decoded).
 *
 * Sits next to [SubRip] (subtitle cues): nothing reads the 10-byte header
 * that opens an ID3v2 tag — version, flag bits, and the syncsafe tag size
 * that tells a scanner where the audio starts. Versions 2.2, 2.3, and 2.4
 * are accepted with per-version flag rules (v2.2 reserves all flag bits,
 * so any set bit is refused; v2.3/v2.4 define unsynchronisation, extended
 * header, and experimental bits, and v2.4 adds the footer bit); reserved
 * violations are refused rather than masked, because a masked bit would
 * silently misreport the tag layout. The size is four syncsafe bytes (top
 * bit of each must be clear); a set top bit means corruption, not a
 * bigger tag. Frame parsing is explicitly outside — this kernel stops
 * after the header.
 *
 * Plain Kotlin (ByteArray in, data out), JVM unit-testable.
 */
object Id3Head {

    /** Parsed header: version, flags, syncsafe size, total header length. */
    data class Header(
        val major: Int,
        val minor: Int,
        val unsync: Boolean,
        val extHeader: Boolean,
        val experimental: Boolean,
        val footer: Boolean,
        val tagSize: Int,
        val headerLength: Int,
    )

    /**
     * Parses the ID3v2 header at the start of [bytes].
     *
     * @throws IllegalArgumentException on short input, a bad magic, an
     * unsupported version, reserved flag bits, or a corrupt syncsafe size.
     */
    fun parse(bytes: ByteArray): Header {
        require(bytes.size >= 10) { "short header" }
        require(bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            "bad magic"
        }
        val major = bytes[3].toInt() and 0xFF
        val minor = bytes[4].toInt() and 0xFF
        require(major in 2..4) { "bad version" }
        val flags = bytes[5].toInt() and 0xFF
        val unsync: Boolean
        val ext: Boolean
        val exp: Boolean
        val footer: Boolean
        when (major) {
            2 -> {
                require(flags == 0) { "reserved flags" }
                unsync = false
                ext = false
                exp = false
                footer = false
            }
            3 -> {
                require(flags and 0x1F == 0) { "reserved flags" }
                unsync = flags and 0x80 != 0
                ext = flags and 0x40 != 0
                exp = flags and 0x20 != 0
                footer = false
            }
            else -> {
                require(flags and 0x0F == 0) { "reserved flags" }
                unsync = flags and 0x80 != 0
                ext = flags and 0x40 != 0
                exp = flags and 0x20 != 0
                footer = flags and 0x10 != 0
            }
        }
        var size = 0
        for (i in 6..9) {
            val b = bytes[i].toInt() and 0xFF
            require(b and 0x80 == 0) { "bad syncsafe" }
            size = size * 128 + b
        }
        return Header(major, minor, unsync, ext, exp, footer, size, 10)
    }
}
