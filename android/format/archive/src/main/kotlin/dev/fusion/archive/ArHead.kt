package dev.fusion.archive

/**
 * Unix `ar` archive structure (the common/GNU format: 8-byte global
 * magic `!<arch>\n`, then 60-byte member headers — name 16, mtime 12,
 * uid 6, gid 6, mode 8, size 10, terminator `` `\n `` — ASCII decimal
 * fields, members padded to EVEN offsets with `\n`). Debian `.deb`
 * files are exactly this outer shape.
 *
 * Declared narrowings: header walking only — no extraction, no GNU
 * long-name table resolution (`//` and `/N` names are REPORTED as
 * special, not resolved), sizes must parse as non-negative decimals.
 */
object ArHead {
    const val SCOPE = "ar/deb outer structure walk only; no extraction; GNU long names reported, not resolved."
    const val MAGIC = "!<arch>\n"

    data class Member(val name: String, val mtime: Long, val size: Long, val dataOffset: Int, val special: Boolean)

    private fun field(b: ByteArray, off: Int, len: Int): String =
        String(b, off, len, Charsets.US_ASCII).trim()

    fun parse(bytes: ByteArray): List<Member> {
        require(bytes.size >= 8) { "shorter than the ar magic" }
        require(String(bytes, 0, 8, Charsets.US_ASCII) == MAGIC) { "not an ar archive" }
        val out = ArrayList<Member>()
        var pos = 8
        while (pos < bytes.size) {
            require(bytes.size - pos >= 60) { "truncated member header at $pos" }
            val name = field(bytes, pos, 16)
            val mtimeS = field(bytes, pos + 16, 12)
            val sizeS = field(bytes, pos + 48, 10)
            require(bytes[pos + 58].toInt() == 0x60 && bytes[pos + 59].toInt() == 0x0A) { "bad header terminator at $pos" }
            require(sizeS.isNotEmpty() && sizeS.all { it.isDigit() }) { "size '$sizeS' not decimal" }
            val size = sizeS.toLong()
            val mtime = if (mtimeS.isEmpty()) 0L else mtimeS.toLongOrNull()
                ?: throw IllegalArgumentException("mtime '$mtimeS' not decimal")
            val dataOffset = pos + 60
            require(dataOffset + size <= bytes.size) { "member '$name' runs past end of archive" }
            val special = name == "//" || (name.startsWith("/") && name.length > 1)
            out.add(Member(name.removeSuffix("/"), mtime, size, dataOffset, special))
            pos = dataOffset + size.toInt()
            if (pos % 2 == 1) {
                require(pos < bytes.size && bytes[pos].toInt() == 0x0A) { "missing even-offset padding at $pos" }
                pos++
            }
        }
        require(out.isNotEmpty()) { "archive with no members" }
        return out
    }

    /** Debian layout check: first member must be `debian-binary`. */
    fun isDeb(members: List<Member>): Boolean =
        members.firstOrNull()?.name == "debian-binary"
}
