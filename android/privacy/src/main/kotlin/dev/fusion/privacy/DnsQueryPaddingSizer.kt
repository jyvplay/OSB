// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Bounded DNS query padding sizer per RFC 8467. Computes the padded wire
 * length for DNS queries (multiple of [BLOCK_SIZE] = 128 bytes) and
 * rejects messages exceeding [MAX_DNS_BYTES] = 4096.
 */
class DnsQueryPaddingSizer private constructor() {
    companion object {
        const val BLOCK_SIZE = 128
        const val MAX_DNS_BYTES = 4096

        data class PaddingResult(
            val originalBytes: Int,
            val paddedBytes: Int,
            val paddingAdded: Int
        )

        fun compute(originalBytes: Int): PaddingResult {
            require(originalBytes in 1..MAX_DNS_BYTES) {
                "DNS message size out of bounds: $originalBytes"
            }
            val padded = ((originalBytes + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE
            val clamped = if (padded > MAX_DNS_BYTES) MAX_DNS_BYTES else padded
            return PaddingResult(originalBytes, clamped, clamped - originalBytes)
        }
    }
}
