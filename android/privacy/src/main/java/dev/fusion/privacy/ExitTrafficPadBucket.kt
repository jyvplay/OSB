// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Website-Fingerprinting & OHTTP / Tor Cell-Alignment Traffic Padding Bucketer.
 *
 * Prevents exact response/request byte-length side channels over Tor (`514`-byte link cell
 * payload multiples) and `RFC 9458` / `RFC 9292` Oblivious HTTP (`power-of-two` / tiered bucket
 * padding) by mapping arbitrary payload sizes to canonical padded target lengths.
 */
object ExitTrafficPadBucket {
    const val SCOPE =
        "Deterministic Tor 514-byte cell multiple and OHTTP tiered power-of-two traffic-padding bucketer."

    const val TOR_CELL_BYTES = 514

    data class PadPlan(
        val originalBytes: Int,
        val targetBytes: Int,
        val padBytesAdded: Int,
        val overheadRatio: Double,
    )

    fun planTorCellMultiple(payloadBytes: Int): PadPlan {
        require(payloadBytes >= 0) { "payloadBytes must be >= 0" }
        val cells = if (payloadBytes == 0) 1 else (payloadBytes + TOR_CELL_BYTES - 1) / TOR_CELL_BYTES
        val target = cells * TOR_CELL_BYTES
        val added = target - payloadBytes
        return PadPlan(payloadBytes, target, added, if (payloadBytes == 0) 1.0 else added.toDouble() / payloadBytes)
    }

    fun planOhttpPowerBucket(payloadBytes: Int, minBucketBytes: Int = 256): PadPlan {
        require(payloadBytes >= 0 && minBucketBytes >= 64 && (minBucketBytes and (minBucketBytes - 1)) == 0) {
            "invalid payloadBytes ($payloadBytes) or non-power-of-two minBucketBytes ($minBucketBytes)"
        }
        var bucket = minBucketBytes
        while (bucket < payloadBytes) {
            require(bucket <= (1 shl 26)) { "payloadBytes $payloadBytes exceeds max bucket" }
            bucket = bucket shl 1
        }
        val added = bucket - payloadBytes
        return PadPlan(payloadBytes, bucket, added, if (payloadBytes == 0) 1.0 else added.toDouble() / payloadBytes)
    }
}
