package dev.fusion.storage

/**
 * Strict hex byte-string codec for storage digests: lowercase output,
 * even length required, case-insensitive decode, NO `0x` prefixes, NO
 * whitespace tolerance — a digest field is exact or it is wrong
 * (ShardPath consumes exactly this shape).
 *
 * Declared narrowings: whole-string codec only; streaming and
 * formatting-with-separators (MAC-style) live in their own kernels.
 */
object HexBytes {
    const val SCOPE = "Strict whole-string hex codec; no prefixes, no whitespace, lowercase canonical."

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4]).append("0123456789abcdef"[v and 0xF])
        }
        return sb.toString()
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex (${hex.length})" }
        require(hex.none { it.isWhitespace() }) { "whitespace in hex string" }
        require(!hex.startsWith("0x") && !hex.startsWith("0X")) { "prefixes refused" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "'${hex[2 * i]}${hex[2 * i + 1]}' is not hex at byte $i" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun isCanonical(hex: String): Boolean =
        hex.length % 2 == 0 && hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' }
}
