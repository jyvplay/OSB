package dev.fusion.format.image

/**
 * Windows ICO/CUR directory parser (ICONDIR + ICONDIRENTRY layout as
 * documented for the Windows icon resource format).
 *
 * Layout: u16le reserved (must be 0), u16le type (1 = icon, 2 = cursor),
 * u16le count (> 0); then `count` 16-byte entries: width u8 (0 => 256),
 * height u8 (0 => 256), colorCount u8, reserved u8, planes u16le,
 * bitCount u16le, bytesInRes u32le (> 0), imageOffset u32le. Each payload
 * is either a BMP fragment (BITMAPINFOHEADER, biSize = 40 u32le) or a whole
 * PNG (8-byte signature); for PNG payloads the IHDR dimensions at offsets
 * 16/20 (u32 big-endian) must agree with the directory entry.
 *
 * Declared narrowings: directory + payload-kind + bounds + PNG-dimension
 * agreement only; no pixel decoding, no mask validation, no cursor hotspot
 * semantics beyond field passthrough.
 */
object IcoDir {
    const val SCOPE = "ICONDIR structure, payload kind, bounds and PNG-IHDR dimension agreement; no pixel decode."
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    enum class Payload { BMP, PNG }

    data class Entry(
        val width: Int, val height: Int, val colorCount: Int,
        val planes: Int, val bitCount: Int, val bytes: Int, val offset: Int, val payload: Payload
    )

    data class Dir(val cursor: Boolean, val entries: List<Entry>)

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    private fun u32(b: ByteArray, i: Int): Long =
        u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)
    private fun u32be(b: ByteArray, i: Int): Long =
        ((u8(b, i).toLong() shl 24) or (u8(b, i + 1).toLong() shl 16) or (u8(b, i + 2).toLong() shl 8) or u8(b, i + 3).toLong())

    fun parse(bytes: ByteArray): Dir {
        require(bytes.size >= 6) { "ICO shorter than ICONDIR" }
        require(u16(bytes, 0) == 0) { "ICONDIR reserved field is not 0" }
        val type = u16(bytes, 2)
        require(type == 1 || type == 2) { "ICONDIR type $type is neither icon nor cursor" }
        val count = u16(bytes, 4)
        require(count > 0) { "ICONDIR with zero images" }
        val headerEnd = 6 + 16 * count
        require(bytes.size >= headerEnd) { "truncated ICONDIRENTRY table" }
        val entries = ArrayList<Entry>(count)
        for (k in 0 until count) {
            val e = 6 + 16 * k
            val w = u8(bytes, e).let { if (it == 0) 256 else it }
            val h = u8(bytes, e + 1).let { if (it == 0) 256 else it }
            require(u8(bytes, e + 3) == 0) { "entry $k reserved byte is not 0" }
            val size = u32(bytes, e + 8)
            val off = u32(bytes, e + 12)
            require(size > 0) { "entry $k has zero payload bytes" }
            require(off >= headerEnd) { "entry $k payload overlaps the directory" }
            require(off + size <= bytes.size) { "entry $k payload runs past end of file" }
            val o = off.toInt()
            val payload: Payload = if (size >= 8 && PNG_MAGIC.indices.all { bytes[o + it] == PNG_MAGIC[it] }) {
                require(size >= 24) { "entry $k PNG payload shorter than signature+IHDR" }
                val pw = u32be(bytes, o + 16).toInt()
                val ph = u32be(bytes, o + 20).toInt()
                require(pw == w && ph == h) { "entry $k PNG is ${pw}x$ph but directory says ${w}x$h" }
                Payload.PNG
            } else {
                require(size >= 4 && u32(bytes, o) == 40L) { "entry $k payload is neither PNG nor BITMAPINFOHEADER" }
                Payload.BMP
            }
            entries.add(Entry(w, h, u8(bytes, e + 2), u16(bytes, e + 4), u16(bytes, e + 6), size.toInt(), o, payload))
        }
        return Dir(cursor = type == 2, entries = entries)
    }

    /** Best entry for a requested edge: smallest entry >= want, else the largest available. */
    fun best(dir: Dir, want: Int): Entry {
        require(want > 0) { "requested size $want" }
        val geq = dir.entries.filter { minOf(it.width, it.height) >= want }
        return geq.minByOrNull { it.width * it.height } ?: dir.entries.maxByOrNull { it.width * it.height }!!
    }
}
