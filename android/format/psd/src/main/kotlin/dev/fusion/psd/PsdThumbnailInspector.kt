// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * R146 — bounded PSD image-resource walker that locates the embedded
 * thumbnail (resource ids 1033 JPEG / 1036 raw) without decoding image data.
 *
 * Walks the 26-byte `8BPS` header, skips color-mode data under a byte cap,
 * then iterates `8BIM` image-resource records with pascal-name and even-size
 * padding, and reports the thumbnail record if one is present. Truncated or
 * inconsistent lengths are refused before any result is returned.
 *
 * Declared narrowings: file signature `8BPS`, version 1 only (PSB refused);
 * 1..56 channels; rows/columns 1..30000; depth in {1,8,16,32}; color mode
 * 0..9; color-mode data <= 16 MiB; image-resources section <= 16 MiB with at
 * most [MAX_RESOURCES] records; thumbnail dimensions 1..4096; compressed size
 * <= total size <= remaining resource bytes; JPEG thumbnails must start with
 * `FFD8FF`.
 */
object PsdThumbnailInspector {
    const val SCOPE =
        "8BPS header + 8BIM resource walk to locate/validate the embedded thumbnail; no pixel decode, no layer parse."

    const val MAX_RESOURCES = 1024
    const val MAX_COLOR_DATA = 1 shl 24
    const val MAX_RESOURCE_SECTION = 1 shl 24
    const val MAX_THUMB_DIM = 4096

    data class Thumbnail(
        val id: Int,
        val format: Int, // 0 raw, 1 jpeg
        val width: Int,
        val height: Int,
        val compressedSize: Long,
    )

    data class Result(val resourceCount: Int, val thumbnail: Thumbnail?)

    private fun u16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xff) shl 8) or (b[at + 1].toInt() and 0xff)

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toInt() and 0xff) shl 24).toLong() and 0xff000000L or
            ((b[at + 1].toInt() and 0xff) shl 16).toLong() or
            ((b[at + 2].toInt() and 0xff) shl 8).toLong() or
            (b[at + 3].toInt() and 0xff).toLong()

    fun inspect(file: ByteArray): Result {
        require(file.size >= 26) { "file shorter than the 8BPS header" }
        require(
            file[0] == '8'.code.toByte() && file[1] == 'B'.code.toByte() &&
                file[2] == 'P'.code.toByte() && file[3] == 'S'.code.toByte()
        ) { "bad 8BPS signature" }
        require(u16(file, 4) == 1) { "PSB/version!=1 refused" }
        val channels = u16(file, 12)
        require(channels in 1..56) { "channel count out of range" }
        val rows = u32(file, 14)
        val cols = u32(file, 18)
        require(rows in 1..30000 && cols in 1..30000) { "dimensions out of range" }
        val depth = u16(file, 22)
        require(depth == 1 || depth == 8 || depth == 16 || depth == 32) { "unsupported depth" }
        val colorMode = u16(file, 24)
        require(colorMode in 0..9) { "unsupported color mode" }

        var pos = 26
        require(pos + 4 <= file.size) { "truncated color-mode length" }
        val colorLen = u32(file, pos)
        require(colorLen <= MAX_COLOR_DATA) { "color-mode data over cap" }
        pos += 4 + colorLen.toInt()
        require(pos <= file.size) { "truncated color-mode data" }

        require(pos + 4 <= file.size) { "truncated image-resource length" }
        val sectionLen = u32(file, pos)
        require(sectionLen <= MAX_RESOURCE_SECTION) { "image-resource section over cap" }
        pos += 4
        val sectionEnd = pos + sectionLen.toInt()
        require(sectionEnd <= file.size) { "truncated image-resource section" }

        var count = 0
        var thumb: Thumbnail? = null
        while (pos < sectionEnd) {
            require(count < MAX_RESOURCES) { "too many image resources" }
            require(pos + 12 <= sectionEnd) { "truncated resource header" }
            require(
                file[pos] == '8'.code.toByte() && file[pos + 1] == 'B'.code.toByte() &&
                    file[pos + 2] == 'I'.code.toByte() && file[pos + 3] == 'M'.code.toByte()
            ) { "bad 8BIM signature" }
            val id = u16(file, pos + 4)
            val nameLen = file[pos + 6].toInt() and 0xff
            // Pascal name: 1 length byte + name, padded so total (len byte +
            // name) is even — i.e. name padded to an odd total with padding
            // counted inside the even rule.
            val nameTotal = 1 + nameLen
            val namePadded = nameTotal + (nameTotal and 1) // round up to even
            val sizeAt = pos + 6 + namePadded
            require(sizeAt + 4 <= sectionEnd) { "truncated resource size" }
            val size = u32(file, sizeAt)
            val dataAt = sizeAt + 4
            require(size <= sectionEnd - dataAt) { "resource data exceeds section" }

            if (id == 1033 || id == 1036) {
                // Thumbnail record: format(2) width(4) height(4) widthBytes(4)
                // totalSize(4) compressedSize(4) = 22 bytes, then payload.
                require(size >= 22) { "thumbnail record too small" }
                val format = u16(file, dataAt)
                require(format == 0 || format == 1) { "unknown thumbnail format" }
                val w = u32(file, dataAt + 2)
                val h = u32(file, dataAt + 6)
                val widthBytes = u32(file, dataAt + 10)
                val totalSize = u32(file, dataAt + 14)
                val compressed = u32(file, dataAt + 18)
                require(w in 1..MAX_THUMB_DIM && h in 1..MAX_THUMB_DIM) { "thumbnail dimensions out of range" }
                require(widthBytes >= 3 && totalSize >= compressed) { "thumbnail size fields inconsistent" }
                require(compressed + 22 <= size) { "thumbnail compressed size exceeds record" }
                if (format == 1) {
                    val j = dataAt + 22
                    require(
                        j + 3 <= sectionEnd &&
                            file[j] == 0xff.toByte() && file[j + 1] == 0xd8.toByte() && file[j + 2] == 0xff.toByte()
                    ) { "jpeg thumbnail missing SOI" }
                }
                thumb = Thumbnail(id, format, w.toInt(), h.toInt(), compressed)
            }

            count++
            val dataEnd = dataAt + size.toInt()
            // Resource data is padded to an even length.
            val next = dataEnd + (size.toInt() and 1)
            require(next > pos) { "resource walk made no forward progress" }
            require(next <= sectionEnd) { "resource padding exceeds section" }
            pos = next
        }
        require(pos == sectionEnd) { "resource walk stopped short of the section end" }
        return Result(count, thumb)
    }

    fun render(r: Result): String {
        val t = r.thumbnail
        val tail = if (t == null) "thumb=none"
        else "thumb=${t.id}:${t.format}:${t.width}:${t.height}:${t.compressedSize}"
        return "nres=${r.resourceCount};$tail"
    }
}
