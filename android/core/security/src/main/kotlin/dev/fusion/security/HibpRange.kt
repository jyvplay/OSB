// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * "Have I Been Pwned" k-anonymity range-response lookup (offline half).
 *
 * Sits next to [PinRules] (PIN policy) and [AttemptWindow] (rate
 * limiting): nothing checks a secret against a breach corpus. The HIBP
 * range API keeps the full hash off the wire — the client sends 5 hex
 * chars and scans the returned `SUFFIX:count` lines locally. [lookup]
 * IS that local scan: the caller supplies the SHA-1 (hashing is caller
 * duty; this kernel never sees a password) and the response body, and
 * gets the breach count for its suffix, or 0 when absent. Fetching,
 * transport, and hash choice are outside; a 0 is "not in THIS corpus",
 * never "unbreached".
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object HibpRange {
    const val MAX_BODY = 1_000_000
    const val MAX_LINES = 100_000

    /**
     * Breach count for [sha1Hex] in a range-response [body].
     *
     * @throws IllegalArgumentException on a malformed SHA-1, a malformed
     * response line, or an over-cap body.
     */
    fun lookup(sha1Hex: String, body: String): Long {
        require(sha1Hex.length == 40 && sha1Hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "bad sha1"
        }
        require(body.length <= MAX_BODY) { "body over cap" }
        val want = sha1Hex.substring(5).uppercase()
        var lines = 0
        for (raw in body.split("\n")) {
            val line = raw.trimEnd('\r')
            if (line.isEmpty()) continue
            lines++
            require(lines <= MAX_LINES) { "too many lines" }
            val sep = line.indexOf(':')
            require(sep == 35) { "bad suffix" }
            val suffix = line.substring(0, 35)
            require(suffix.all { it.isDigit() || it in 'A'..'F' }) { "bad suffix" }
            val count = line.substring(36).toLongOrNull()
            require(count != null && count >= 0) { "bad count" }
            if (suffix == want) return count
        }
        return 0L
    }
}
