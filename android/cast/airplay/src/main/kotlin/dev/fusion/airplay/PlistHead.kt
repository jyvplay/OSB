package dev.fusion.airplay

/**
 * Binary-plist envelope parser (Apple CoreFoundation `bplist00` format;
 * layout documented in Apple's open-source CFBinaryPList.c).
 *
 * File shape: 8-byte header "bplist" + 2 version digits; object table;
 * offset table; 32-byte trailer = 5 unused bytes, sortVersion u8,
 * offsetIntSize u8, objectRefSize u8, numObjects u64be, topObject u64be,
 * offsetTableOffset u64be.
 *
 * Complements AirPlayUrlSender, which parses TEXT plists only and
 * returns binary ones unparsed — this kernel makes those bytes at least
 * structurally checkable before anything walks them.
 *
 * Declared narrowings: envelope + trailer geometry only — object graph
 * decoding is NOT performed (that is a future, separately-tested step);
 * version "00" only (the writer formats Apple ships).
 */
object PlistHead {
    const val SCOPE = "bplist00 envelope + trailer geometry only; no object-graph decode."

    data class Trailer(
        val sortVersion: Int, val offsetIntSize: Int, val objectRefSize: Int,
        val numObjects: Long, val topObject: Long, val offsetTableOffset: Long
    )

    private fun u64(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }

    fun parse(bytes: ByteArray): Trailer {
        require(bytes.size >= 8 + 32 + 1) { "too short for header + one object + trailer" }
        val header = String(bytes, 0, 8, Charsets.US_ASCII)
        require(header.startsWith("bplist")) { "not a binary plist (header '$header')" }
        require(header.substring(6) == "00") { "bplist version '${header.substring(6)}' outside declared 00 scope" }
        val t = bytes.size - 32
        val sortVersion = bytes[t + 5].toInt() and 0xFF
        val offsetIntSize = bytes[t + 6].toInt() and 0xFF
        val objectRefSize = bytes[t + 7].toInt() and 0xFF
        require(offsetIntSize in 1..8) { "offsetIntSize $offsetIntSize outside 1..8" }
        require(objectRefSize in 1..8) { "objectRefSize $objectRefSize outside 1..8" }
        val numObjects = u64(bytes, t + 8)
        val topObject = u64(bytes, t + 16)
        val tableOffset = u64(bytes, t + 24)
        require(numObjects > 0) { "zero objects" }
        require(topObject < numObjects) { "topObject $topObject >= numObjects $numObjects" }
        require(tableOffset >= 8) { "offset table inside the header" }
        require(tableOffset + numObjects * offsetIntSize <= t) {
            "offset table (${numObjects}x$offsetIntSize at $tableOffset) runs into the trailer"
        }
        return Trailer(sortVersion, offsetIntSize, objectRefSize, numObjects, topObject, tableOffset)
    }

    fun isBinaryPlist(bytes: ByteArray): Boolean =
        bytes.size >= 8 && String(bytes, 0, 6, Charsets.US_ASCII) == "bplist"
}
