// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Clean-room Cross-Kernel Canonical Transcript Aggregator & Permutation-Invariance Verifier for
 * R50 Host Determinism Audits.
 */
object R50CrossKernelTranscriptVerifier {
    const val SCOPE =
        "Pure-JVM SHA-256 canonical key-value transcript digest and insertion-order permutation invariance verifier."

    fun canonicalDigestHex(entries: List<Pair<String, String>>): String {
        require(entries.isNotEmpty()) { "transcript entries must be non-empty" }
        val canonical = entries
            .map { (k, v) ->
                require(k.isNotBlank() && '\n' !in k) { "invalid transcript key '$k'" }
                "${k.trim()}=${v.replace("\r\n", "\n")}"
            }
            .sorted()
            .joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun verifyPermutationInvariant(runA: List<Pair<String, String>>, runB: List<Pair<String, String>>): Pair<Boolean, String> {
        val ha = canonicalDigestHex(runA)
        val hb = canonicalDigestHex(runB)
        return (ha == hb) to ha
    }
}
