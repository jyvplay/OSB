// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic HMAC pseudonyms for analytics-safe identifiers.
 *
 * Sits next to [LogScrubber] (which *removes* identifiers from logs): some
 * pipelines need to *correlate* events without storing the raw identifier —
 * crash buckets per user, abuse counters per device — and nothing derives a
 * stable opaque token for that. The pseudonym is the first 16 bytes of
 * HMAC-SHA256(key, context ‖ 0x00 ‖ value), hex-encoded; the context label
 * domain-separates token spaces so a pseudonym minted for crash buckets is
 * useless as an abuse-counter key. Keys are caller-supplied (16–64 bytes) and
 * values are capped at 1 KiB. This is keyed pseudonymization, not anonymization:
 * anyone holding the key can re-derive the mapping, which is documented so no
 * caller mistakes these tokens for anonymous IDs.
 *
 * Javax-crypto only, no Android dependency, JVM unit-testable.
 */
object Pseudonym {

    /**
     * Derives the 32-hex pseudonym for [value] under [key] and [context].
     *
     * @throws IllegalArgumentException on a short/long key, a blank context,
     * or an empty/oversized value.
     */
    fun derive(key: ByteArray, context: String, value: String): String {
        require(key.size in 16..64) { "bad key length" }
        require(context.isNotBlank()) { "blank context" }
        val raw = value.toByteArray(Charsets.UTF_8)
        require(raw.isNotEmpty() && raw.size <= 1024) { "bad value length" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(context.toByteArray(Charsets.UTF_8))
        mac.update(0.toByte())
        val full = mac.doFinal(raw)
        return full.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Re-derives and compares in constant time. Returns false rather than
     * throwing when [candidate] is merely wrong.
     */
    fun matches(key: ByteArray, context: String, value: String, candidate: String): Boolean {
        val expected = derive(key, context, value)
        if (candidate.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].code xor candidate[i].code)
        return diff == 0
    }
}
