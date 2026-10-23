// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Magnet-link parser for the beam module, pinned to BEP 9 (fetched from
 * bittorrent.org 2026-10-23, version cfd7cd53):
 *
 *   v1: magnet:?xt=urn:btih:<info-hash>&dn=<name>&tr=<tracker>&x.pe=<peer>
 *   v2: magnet:?xt=urn:btmh:<tagged-info-hash>&...
 *
 * - `xt` is the only mandatory parameter.
 * - v1 info-hash is 40 hex characters; "for compatability with existing
 *   links in the wild, clients should also support the 32 character
 *   base32 encoded info-hash" (decoded here to the same 40-hex form).
 * - v2 tagged-info-hash is a hex multihash; for SHA2-256 that is
 *   "1220" + 64 hex digits (0x12 = sha2-256, 0x20 = 32 bytes).
 * - `dn`, `tr`, `x.pe` are optional; `tr` and `x.pe` repeat; btih and
 *   btmh "may exist in the same magnet if they describe the same hybrid
 *   torrent", so one of each is accepted but duplicates of either refuse.
 *
 * Percent-decoding matches Python urllib.parse.parse_qsl: %XX (strict two
 * hex digits) and '+' as space. Anything else malformed refuses with
 * IllegalArgumentException. Pure JVM logic; no network, no DHT, no I/O.
 */
object MagnetParse {
    data class Magnet(
        val btih: String?,
        val btmh: String?,
        val displayName: String?,
        val trackers: List<String>,
        val peers: List<String>,
    )

    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val HEX = "0123456789abcdef"

    fun parse(uri: String): Magnet {
        require(uri.startsWith("magnet:?")) { "not a magnet URI" }
        val query = uri.substring("magnet:?".length)
        require(query.isNotEmpty()) { "empty query" }
        var btih: String? = null
        var btmh: String? = null
        var dn: String? = null
        val trackers = ArrayList<String>()
        val peers = ArrayList<String>()
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            require(eq > 0) { "parameter without value: $pair" }
            val key = pair.substring(0, eq)
            val value = pctDecode(pair.substring(eq + 1))
            when (key) {
                "xt" -> when {
                    value.startsWith("urn:btih:") -> {
                        require(btih == null) { "duplicate btih" }
                        btih = normalizeV1(value.substring("urn:btih:".length))
                    }
                    value.startsWith("urn:btmh:") -> {
                        require(btmh == null) { "duplicate btmh" }
                        btmh = normalizeV2(value.substring("urn:btmh:".length))
                    }
                    else -> throw IllegalArgumentException("unsupported xt urn: $value")
                }
                "dn" -> {
                    require(dn == null) { "duplicate dn" }
                    dn = value
                }
                "tr" -> trackers.add(value)
                "x.pe" -> peers.add(value)
                else -> Unit // unrecognized parameters are ignored, not errors
            }
        }
        require(btih != null || btmh != null) { "xt is the only mandatory parameter and it is missing" }
        return Magnet(btih, btmh, dn, trackers, peers)
    }

    /** 40-hex (case-folded) or 32-char base32 (decoded); anything else refuses. */
    fun normalizeV1(hash: String): String = when (hash.length) {
        40 -> {
            require(hash.all { it in HEX || it in "ABCDEF" }) { "bad hex info-hash" }
            hash.lowercase()
        }
        32 -> {
            val bytes = base32Decode(hash.uppercase())
            bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
        }
        else -> throw IllegalArgumentException("btih must be 40 hex or 32 base32 chars, got ${hash.length}")
    }

    /** Multihash sha2-256: "1220" + 64 hex; other multihash prefixes refuse. */
    fun normalizeV2(hash: String): String {
        val low = hash.lowercase()
        require(low.length == 68 && low.startsWith("1220")) { "btmh must be 1220 + 64 hex" }
        require(low.all { it in HEX }) { "bad hex in btmh" }
        return low
    }

    /** RFC 4648 base32, no padding needed: 32 chars = 160 bits exactly. */
    fun base32Decode(s: String): ByteArray {
        require(s.length == 32) { "base32 info-hash must be 32 chars" }
        var acc = 0L
        var bits = 0
        val out = ByteArray(20)
        var i = 0
        for (c in s) {
            val v = BASE32.indexOf(c)
            require(v >= 0) { "bad base32 char: $c" }
            acc = (acc shl 5) or v.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[i++] = ((acc shr bits) and 0xFF).toByte()
            }
        }
        return out
    }

    fun pctDecode(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '+' -> { sb.append(' '); i++ }
                '%' -> {
                    require(i + 2 < s.length) { "truncated percent escape" }
                    val hi = Character.digit(s[i + 1], 16)
                    val lo = Character.digit(s[i + 2], 16)
                    require(hi >= 0 && lo >= 0) { "bad percent escape" }
                    sb.append(((hi shl 4) or lo).toChar())
                    i += 3
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
