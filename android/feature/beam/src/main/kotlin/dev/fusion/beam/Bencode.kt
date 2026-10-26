// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Strict bencode decoder, pinned to BEP 3 (fetched from bittorrent.org
 * 2026-10-23, version 0e08ddf8):
 *
 * - Strings are length-prefixed base ten followed by ':' ("4:spam").
 * - Integers are 'i' <base-10> 'e'; "i-0e is invalid. All encodings with
 *   a leading zero, such as i03e, are invalid, other than i0e".
 * - Lists are 'l' ... 'e'; dicts are 'd' ... 'e' and "keys must be
 *   strings and appear in sorted order (sorted as raw strings, not
 *   alphanumerics)".
 * - BEP 3 (2017 revision) requires validators to reject invalid metainfo
 *   outright: unsorted or duplicate keys, leading zeros, and trailing
 *   bytes all refuse here; no decode-encode round-trip is ever performed
 *   on invalid data.
 *
 * Declared bounds (narrower than the spec, stated honestly): integers
 * must fit in a signed 64-bit Long ("integers have no size limitation"
 * in BEP 3; bignum torrents refuse here), nesting depth is capped at 32,
 * and string lengths are capped at 8 MiB. Pure JVM logic; no I/O.
 */
object Bencode {
    const val MAX_DEPTH = 32
    const val MAX_STRING = 8 * 1024 * 1024

    sealed class BValue {
        data class BInt(val value: Long) : BValue()
        data class BStr(val bytes: ByteArray) : BValue() {
            val text: String get() = String(bytes, Charsets.UTF_8)
            override fun equals(other: Any?) = other is BStr && bytes.contentEquals(other.bytes)
            override fun hashCode() = bytes.contentHashCode()
        }
        data class BList(val items: List<BValue>) : BValue()
        data class BDict(val entries: List<Pair<BStr, BValue>>) : BValue() {
            operator fun get(key: String): BValue? =
                entries.firstOrNull { it.first.text == key }?.second
        }
    }

    fun decode(data: ByteArray): BValue {
        val p = Parser(data)
        val v = p.value(0)
        require(p.pos == data.size) { "trailing bytes after bencoded value" }
        return v
    }

    private class Parser(val data: ByteArray) {
        var pos = 0

        fun value(depth: Int): BValue {
            require(depth < MAX_DEPTH) { "nesting too deep" }
            require(pos < data.size) { "truncated value" }
            return when (val c = data[pos].toInt().toChar()) {
                'i' -> integer()
                'l' -> list(depth)
                'd' -> dict(depth)
                in '0'..'9' -> string()
                else -> throw IllegalArgumentException("unexpected byte '$c' at $pos")
            }
        }

        fun integer(): BValue.BInt {
            pos++ // 'i'
            val start = pos
            if (pos < data.size && data[pos].toInt().toChar() == '-') pos++
            while (pos < data.size && data[pos].toInt().toChar() in '0'..'9') pos++
            require(pos < data.size && data[pos].toInt().toChar() == 'e') { "unterminated integer" }
            val text = String(data, start, pos - start, Charsets.US_ASCII)
            pos++ // 'e'
            require(text.isNotEmpty() && text != "-") { "empty integer" }
            require(text != "-0") { "i-0e is invalid" }
            val digits = if (text.startsWith("-")) text.substring(1) else text
            require(digits == "0" || !digits.startsWith("0")) { "leading zero in integer" }
            return BValue.BInt(text.toLongOrNull() ?: throw IllegalArgumentException("integer exceeds signed 64-bit scope"))
        }

        fun string(): BValue.BStr {
            val start = pos
            while (pos < data.size && data[pos].toInt().toChar() in '0'..'9') pos++
            require(pos < data.size && data[pos].toInt().toChar() == ':') { "string length without colon" }
            val lenText = String(data, start, pos - start, Charsets.US_ASCII)
            require(lenText == "0" || !lenText.startsWith("0")) { "leading zero in string length" }
            val len = lenText.toLongOrNull() ?: throw IllegalArgumentException("string length overflow")
            require(len in 0..MAX_STRING.toLong()) { "string length out of bounds: $len" }
            pos++ // ':'
            require(pos + len <= data.size) { "truncated string" }
            val bytes = data.copyOfRange(pos, pos + len.toInt())
            pos += len.toInt()
            return BValue.BStr(bytes)
        }

        fun list(depth: Int): BValue.BList {
            pos++ // 'l'
            val items = ArrayList<BValue>()
            while (true) {
                require(pos < data.size) { "unterminated list" }
                if (data[pos].toInt().toChar() == 'e') { pos++; return BValue.BList(items) }
                items.add(value(depth + 1))
            }
        }

        fun dict(depth: Int): BValue.BDict {
            pos++ // 'd'
            val entries = ArrayList<Pair<BValue.BStr, BValue>>()
            var prev: ByteArray? = null
            while (true) {
                require(pos < data.size) { "unterminated dict" }
                if (data[pos].toInt().toChar() == 'e') { pos++; return BValue.BDict(entries) }
                require(data[pos].toInt().toChar() in '0'..'9') { "dict key must be a string" }
                val key = string()
                val p = prev
                if (p != null) {
                    val cmp = rawCompare(p, key.bytes)
                    require(cmp < 0) { if (cmp == 0) "duplicate dict key" else "dict keys not in sorted raw order" }
                }
                prev = key.bytes
                entries.add(key to value(depth + 1))
            }
        }
    }

    /** Bytewise unsigned comparison: "sorted as raw strings, not alphanumerics". */
    fun rawCompare(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
