// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/** One image-resource block header. Block data is counted, never decoded. */
data class ImageResource(val id: Int, val name: String, val size: Int)

/**
 * Walk of the PSD image-resource section that PsdCompositeEngine skips:
 * `8BIM` signature, big-endian id, even-padded Pascal name, big-endian
 * data size. Returns headers only; payload bytes are bounds-checked and
 * skipped. Caps: 1024 blocks, 16 MiB section. Non-8BIM signatures refused.
 */
object PsdResources {
    const val MAX_BLOCKS = 1024
    const val MAX_SECTION = 16 * 1024 * 1024

    fun parse(section: ByteArray): List<ImageResource> {
        require(section.size <= MAX_SECTION) { "section over $MAX_SECTION bytes" }
        val out = mutableListOf<ImageResource>()
        var i = 0
        while (i < section.size) {
            require(out.size < MAX_BLOCKS) { "over $MAX_BLOCKS blocks" }
            require(i + 4 <= section.size && section[i] == '8'.code.toByte() &&
                section[i + 1] == 'B'.code.toByte() && section[i + 2] == 'I'.code.toByte() &&
                section[i + 3] == 'M'.code.toByte()) { "bad signature at $i" }
            require(i + 6 <= section.size) { "truncated id at $i" }
            val id = u16(section, i + 4)
            i += 6
            require(i < section.size) { "truncated name at $i" }
            val nameLen = section[i].toInt() and 0xFF
            require(i + 1 + nameLen <= section.size) { "truncated name at $i" }
            val name = section.decodeToString(i + 1, i + 1 + nameLen)
            require(name.all { it.code in 0x20..0x7E }) { "non-ASCII name at $i" }
            i += 1 + nameLen
            if ((1 + nameLen) % 2 == 1) {
                require(i < section.size) { "truncated name pad at $i" }
                i++
            }
            require(i + 4 <= section.size) { "truncated size at $i" }
            val size = u32(section, i)
            i += 4
            require(size >= 0 && size <= section.size - i) { "block overruns section at $i" }
            i += size
            if (size % 2 == 1) {
                require(i < section.size) { "truncated data pad at $i" }
                i++
            }
            out += ImageResource(id, name, size)
        }
        return out
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
