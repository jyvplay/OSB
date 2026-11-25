// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Bounded 2-level content-addressed shard path mapper (`ab/cd/<hash>`).
 */
class StoragePathShardMapper private constructor() {
    companion object {
        private val HEX_RE = Regex("^[0-9a-f]{8,128}$")

        fun shardPath(hexDigest: String): String {
            val norm = hexDigest.lowercase()
            require(HEX_RE.matches(norm)) { "invalid hex digest" }
            return "${norm.substring(0, 2)}/${norm.substring(2, 4)}/$norm"
        }
    }
}
