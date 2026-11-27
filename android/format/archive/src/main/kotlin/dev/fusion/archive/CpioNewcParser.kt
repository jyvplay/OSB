// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * R146 — bounded POSIX cpio `newc`/`crc` archive header walker.
 *
 * Reads the fixed 110-byte ASCII header (`070701` magic, or `070702` for the
 * checksum variant), the NUL-terminated member name and the member payload,
 * honouring the 4-byte padding rules, and refuses anything malformed before
 * a member list is published. Nothing is extracted to disk and no member
 * payload is retained: sizes, metadata and names only.
 *
 * Declared narrowings: stream must contain the `TRAILER!!!` terminator and
 * end at or right after its padding; at most [MAX_ENTRIES] real members;
 * name (without NUL) <= [MAX_NAME] bytes and cannot contain an interior NUL;
 * member size <= [MAX_MEMBER_BYTES]; every count field must be lowercase or
 * uppercase hex with no sign; header magic must be exactly `070701`/`070702`.
 */
object CpioNewcParser {
    const val SCOPE =
        "Bounded cpio newc/crc header+name+size walker with trailer enforcement; payload bytes are counted and discarded, never extracted."

    const val HEADER_BYTES = 110
    const val MAX_ENTRIES = 4095 // trailer excluded from this count
    const val MAX_NAME = 255
    const val MAX_MEMBER_BYTES = 1 shl 30 // 1 GiB per member
    const val MAX_STREAM_BYTES = 1 shl 30 // 1 GiB total stream

    private val HEX = Regex("[0-9a-fA-F]+")

    data class Member(
        val name: String,
        val size: Long,
        val mode: Long,
        val uid: Long,
        val gid: Long,
        val mtime: Long,
        val ino: Long,
    )

    data class Result(val members: List<Member>, val sawTrailer: Boolean)

    private fun hex8(bytes: ByteArray, at: Int): Long {
        val slice = String(bytes, at, 8, Charsets.US_ASCII)
        require(HEX.matches(slice)) { "non-hex count field at offset $at" }
        return slice.toLong(16)
    }

    private fun pad4(n: Long): Int = ((4 - (n % 4)) % 4).toInt()

    fun parse(stream: ByteArray): Result {
        require(stream.size <= MAX_STREAM_BYTES) { "stream over ${MAX_STREAM_BYTES} bytes" }
        var pos = 0
        val members = ArrayList<Member>()
        var sawTrailer = false
        while (true) {
            require(pos + HEADER_BYTES <= stream.size) { "truncated cpio header" }
            val magic = String(stream, pos, 6, Charsets.US_ASCII)
            require(magic == "070701" || magic == "070702") { "bad cpio magic: $magic" }
            val ino = hex8(stream, pos + 6)
            val mode = hex8(stream, pos + 14)
            val uid = hex8(stream, pos + 22)
            val gid = hex8(stream, pos + 30)
            val nlink = hex8(stream, pos + 38)
            val mtime = hex8(stream, pos + 46)
            val fileSize = hex8(stream, pos + 54)
            hex8(stream, pos + 62)  // devmajor
            hex8(stream, pos + 70)  // devminor
            hex8(stream, pos + 78)  // rdevmajor
            hex8(stream, pos + 86)  // rdevminor
            val nameSize = hex8(stream, pos + 94)
            hex8(stream, pos + 102) // check (crc variant validates it; newc ignores)
            require(nameSize in 2..(MAX_NAME + 1L)) { "cpio namesize out of range" }
            require(fileSize <= MAX_MEMBER_BYTES) { "cpio member over $MAX_MEMBER_BYTES bytes" }
            require(nlink >= 1) { "cpio nlink must be >= 1" }
            val nameSizeInt = nameSize.toInt()

            val nameStart = pos + HEADER_BYTES
            require(nameStart + nameSizeInt <= stream.size) { "truncated cpio name" }
            val nameBytes = stream.copyOfRange(nameStart, nameStart + nameSizeInt)
            require(nameBytes.last() == 0.toByte()) { "cpio name missing NUL terminator" }
            for (i in 0 until nameBytes.size - 1) {
                require(nameBytes[i] != 0.toByte()) { "cpio name has interior NUL" }
            }
            val name = String(nameBytes, 0, nameBytes.size - 1, Charsets.UTF_8)

            val afterName = nameStart + nameSizeInt
            // cpio pads header+name to a 4-byte boundary measured from 0
            // (pos is always a multiple of 4 on entry).
            val dataStart = afterName + pad4(afterName.toLong())
            require(dataStart <= stream.size) { "truncated cpio name padding" }
            val dataEndL = dataStart.toLong() + fileSize
            require(dataEndL <= stream.size) { "truncated cpio member payload" }
            require(dataEndL <= MAX_STREAM_BYTES) { "member end over stream cap" }
            val dataEnd = dataEndL.toInt()

            if (name == "TRAILER!!!") {
                sawTrailer = true
                // Trailer must be last: only its 4-byte padding plus at most
                // one 512-byte writer block of zero fill may remain.
                val trailerEnd = dataEnd + pad4(dataEnd.toLong())
                require(trailerEnd <= stream.size) { "truncated trailer padding" }
                val tail = stream.size - trailerEnd
                require(tail <= 511) { "bytes after cpio trailer" }
                for (i in trailerEnd until stream.size) {
                    require(stream[i] == 0.toByte()) { "non-zero fill after cpio trailer" }
                }
                require(members.size <= MAX_ENTRIES) { "too many cpio members" }
                return Result(members.toList(), sawTrailer)
            }

            require(members.size < MAX_ENTRIES) { "too many cpio members: $MAX_ENTRIES" }
            members.add(Member(name, fileSize, mode, uid, gid, mtime, ino))
            val next = dataEnd + pad4(dataEnd.toLong())
            require(next > pos) { "cpio stream made no forward progress" }
            pos = next
            require(pos <= stream.size) { "truncated cpio member padding" }
        }
    }

    /** Canonical per-member line for the host probe. */
    fun render(m: Member): String =
        "${m.ino}:${m.mode.toString(16)}:${m.uid}:${m.gid}:${m.mtime}:${m.size}:${m.name}"
}
