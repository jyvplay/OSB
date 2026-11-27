// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * R146 — Subresource Integrity (`integrity` attribute) parser (owner:v2.0
 * browser hardening; clean-room from the W3C SRI grammar).
 *
 * Parses the space-separated token list `algo-base64` where algo is one of
 * `sha256`, `sha384`, `sha512` and base64 is the RFC 4648 alphabet with
 * correct padding, decoding to a digest of exactly 32/48/64 bytes. The
 * result is the canonical `algo:hex` list a fetch-time verifier would
 * compare against a locally computed digest. Nothing is fetched and no
 * digest is computed here — parsing only, so a hostile page cannot make this
 * kernel do unbounded hashing.
 *
 * Declared narrowings: input <= 1024 chars; at most 8 tokens; token grammar
 * `sha(256|384|512)-[A-Za-z0-9+/]+={0,2}`; padding bits must be zero; decoded
 * length must equal the algorithm's digest size; duplicate algorithms are
 * refused (SRI uses them as an ordered OR, but duplicates are always a
 * markup bug and refusing keeps verification honest).
 */
object SubresourceIntegrityParser {
    const val SCOPE =
        "Strict SRI token parse to algo:hex; no network, no hashing, no fallback to weaker algorithms."

    const val MAX_INPUT = 1024
    const val MAX_TOKENS = 8

    private val ALGO_LEN = mapOf("sha256" to 32, "sha384" to 48, "sha512" to 64)
    private val TOKEN_RE = Regex("sha(256|384|512)-([A-Za-z0-9+/]+={0,2})")

    fun parse(integrity: String): String {
        require(integrity.length <= MAX_INPUT) { "integrity over $MAX_INPUT chars" }
        val trimmed = integrity.trim()
        require(trimmed.isNotEmpty()) { "empty integrity attribute" }
        val tokens = trimmed.split(Regex("\\s+"))
        require(tokens.size <= MAX_TOKENS) { "more than $MAX_TOKENS integrity tokens" }
        val seen = HashSet<String>()
        val out = ArrayList<String>(tokens.size)
        for (token in tokens) {
            val m = TOKEN_RE.matchEntire(token)
                ?: throw IllegalArgumentException("bad integrity token: $token")
            val algo = "sha${m.groupValues[1]}"
            require(seen.add(algo)) { "duplicate integrity algorithm: $algo" }
            val b64 = m.groupValues[2]
            val bytes = decodeBase64(b64)
            val expect = ALGO_LEN.getValue(algo)
            require(bytes.size == expect) {
                "$algo digest must decode to $expect bytes, got ${bytes.size}"
            }
            out.add("$algo:${bytes.joinToString("") { b -> "%02x".format(b) }}")
        }
        return out.joinToString(",")
    }

    /** Strict RFC 4648 decode: alphabet, padding in place, zero pad bits. */
    private fun decodeBase64(s: String): ByteArray {
        require(s.isNotEmpty()) { "empty base64 digest" }
        require(s.length % 4 == 0) { "base64 length must be a multiple of 4" }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val table = IntArray(128) { -1 }
        for ((i, c) in alphabet.withIndex()) table[c.code] = i

        var pad = 0
        if (s.endsWith("==")) pad = 2 else if (s.endsWith('=')) pad = 1
        val body = s.substring(0, s.length - pad)
        require('=' !in body) { "padding before the final block" }

        val out = ByteArray(s.length / 4 * 3 - pad)
        var o = 0
        var i = 0
        while (i < s.length) {
            val lastBlock = i + 4 == s.length
            val v0 = b64val(table, s[i])
            val v1 = b64val(table, s[i + 1])
            out[o++] = ((v0 shl 2) or (v1 shr 4)).toByte()
            if (lastBlock && pad == 2) {
                require(s[i + 2] == '=' && s[i + 3] == '=') { "padding must fill the tail" }
                require((v1 and 0x0f) == 0) { "non-zero base64 pad bits" }
            } else if (lastBlock && pad == 1) {
                require(s[i + 2] != '=' && s[i + 3] == '=') { "padding must fill the tail" }
                val v2 = b64val(table, s[i + 2])
                require((v2 and 0x03) == 0) { "non-zero base64 pad bits" }
                out[o++] = (((v1 and 0x0f) shl 4) or (v2 shr 2)).toByte()
            } else {
                require(s[i + 2] != '=' && s[i + 3] != '=') { "padding before the final block" }
                val v2 = b64val(table, s[i + 2])
                val v3 = b64val(table, s[i + 3])
                out[o++] = (((v1 and 0x0f) shl 4) or (v2 shr 2)).toByte()
                out[o++] = (((v2 and 0x03) shl 6) or v3).toByte()
            }
            i += 4
        }
        require(o == out.size) { "base64 length disagrees with padding" }
        return out
    }

    private fun b64val(table: IntArray, c: Char): Int {
        require(c.code < 128 && table[c.code] >= 0) { "invalid base64 character" }
        return table[c.code]
    }
}
