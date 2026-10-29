// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Clean-room RFC 6763 §6 DNS-SD TXT resource-record binary wire encoder & decoder for `:cast:airplay`
 * (`_airplay._tcp` / `_raop._tcp` service discovery metadata).
 *
 * Implements exact RFC 6763 §6 rules:
 * - Each character-string is `[len: u8][bytes...]` (`len <= 255`); a single `0x00` empty RDATA
 *   placeholder decodes cleanly to an empty map (§6.1).
 * - Printable US-ASCII keys (`0x20..0x7E` excluding `'='`, at least 1 character).
 * - Distinguishes the three normative §6.4 value forms:
 *   1. [AttrValue.BooleanFlag] (`"key"`, no `'='`),
 *   2. [AttrValue.PresentEmpty] (`"key="`),
 *   3. [AttrValue.Bytes] (`"key=value"`, arbitrary UTF-8 or binary value bytes).
 * - Case-insensitive **first-key-wins** deduplication (§6.4: if a key appears more than once,
 *   the first occurrence wins and later duplicates are ignored).
 */
object TxtRecord {
    const val SCOPE =
        "RFC 6763 §6 DNS-SD binary TXT RDATA encoder/decoder (<=255B strings, flag/empty/bytes value distinction, case-insensitive first-key-wins); mDNS packet compression pointers out of scope."

    const val MAX_RDATA_BYTES = 8_192
    const val MAX_STRING_BYTES = 255

    sealed interface AttrValue {
        data object BooleanFlag : AttrValue
        data object PresentEmpty : AttrValue
        data class Bytes(val raw: ByteArray) : AttrValue {
            fun utf8(): String = raw.toString(StandardCharsets.UTF_8)
            override fun equals(other: Any?): Boolean = other is Bytes && raw.contentEquals(other.raw)
            override fun hashCode(): Int = raw.contentHashCode()
        }

        companion object {
            fun ofUtf8(s: String): AttrValue =
                if (s.isEmpty()) PresentEmpty else Bytes(s.toByteArray(StandardCharsets.UTF_8))
        }
    }

    data class Entry(val key: String, val value: AttrValue) {
        init {
            validateKey(key)
        }
    }

    fun encode(entries: List<Entry>): ByteArray {
        if (entries.isEmpty()) return byteArrayOf(0) // RFC 6763 §6.1 non-empty RDATA single zero byte
        val out = ByteArrayOutputStream()
        val seenLower = HashSet<String>()
        for (e in entries) {
            val lower = e.key.lowercase()
            require(seenLower.add(lower)) { "duplicate key '${e.key}' on encode" }
            val keyBytes = e.key.toByteArray(StandardCharsets.US_ASCII)
            val wire = when (val v = e.value) {
                AttrValue.BooleanFlag -> keyBytes
                AttrValue.PresentEmpty -> keyBytes + byteArrayOf('='.code.toByte())
                is AttrValue.Bytes -> keyBytes + byteArrayOf('='.code.toByte()) + v.raw
            }
            require(wire.size in 1..MAX_STRING_BYTES) {
                "TXT entry '${e.key}' length ${wire.size} exceeds $MAX_STRING_BYTES bytes"
            }
            require(out.size() + 1 + wire.size <= MAX_RDATA_BYTES) {
                "TXT RDATA exceeds $MAX_RDATA_BYTES bytes"
            }
            out.write(wire.size)
            out.write(wire)
        }
        return out.toByteArray()
    }

    fun decode(rdata: ByteArray): Map<String, AttrValue> {
        require(rdata.size <= MAX_RDATA_BYTES) { "TXT RDATA length ${rdata.size} exceeds $MAX_RDATA_BYTES" }
        val result = LinkedHashMap<String, AttrValue>()
        var i = 0
        while (i < rdata.size) {
            val len = rdata[i++].toInt() and 0xFF
            require(i + len <= rdata.size) { "truncated DNS-SD TXT string at offset ${i - 1} (len=$len)" }
            if (len == 0) continue // RFC 6763 §6.1 silently ignore zero-length string
            val slice = rdata.copyOfRange(i, i + len)
            i += len

            val eqIdx = slice.indexOf('='.code.toByte())
            if (eqIdx == 0) continue // RFC 6763 §6.4 strings beginning with '=' are ignored
            val keyLen = if (eqIdx < 0) slice.size else eqIdx
            val key = String(slice, 0, keyLen, StandardCharsets.US_ASCII)
            validateKey(key)

            val lower = key.lowercase()
            if (lower in result) continue // RFC 6763 §6.4 first-key-wins

            val attr = when {
                eqIdx < 0 -> AttrValue.BooleanFlag
                eqIdx == slice.size - 1 -> AttrValue.PresentEmpty
                else -> AttrValue.Bytes(slice.copyOfRange(eqIdx + 1, slice.size))
            }
            result[lower] = attr
        }
        return result
    }

    private fun ByteArray.indexOf(target: Byte): Int {
        for (idx in indices) if (this[idx] == target) return idx
        return -1
    }

    private fun validateKey(key: String) {
        require(key.isNotEmpty() && key.length <= MAX_STRING_BYTES) { "invalid DNS-SD TXT key length" }
        require(key.all { it.code in 0x20..0x7E && it != '=' }) {
            "DNS-SD TXT key '$key' contains illegal non-printable ASCII or '='"
        }
    }
}
