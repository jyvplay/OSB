// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.security.MessageDigest

/**
 * Content-addressed storage (**CAS**) shard planner + refcounted garbage collector (`ab/cd/<sha256>`)
 * backing deduplicated **Recall** media blobs and Tails-clean document snapshots.
 */
object ContentAddressedBlobCasStore {
    const val SCOPE =
        "Two-level fan-out SHA-256 content-addressed blob store with reference counting and orphan garbage collection."

    data class CasObject(val sha256Hex: String, val shardRelativePath: String, val sizeBytes: Int, val refCount: Int)

    class Store {
        private val blobs = LinkedHashMap<String, ByteArray>()
        private val refs = LinkedHashMap<String, Int>()

        fun put(payload: ByteArray): CasObject {
            val digest = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            if (!blobs.containsKey(digest)) {
                blobs[digest] = payload.copyOf()
                refs[digest] = 1
            } else {
                refs[digest] = (refs[digest] ?: 0) + 1
            }
            return describe(digest)
        }

        fun release(sha256Hex: String): Int {
            val key = sha256Hex.lowercase()
            val cur = refs[key] ?: return 0
            val next = (cur - 1).coerceAtLeast(0)
            refs[key] = next
            return next
        }

        fun sweepZeroRefBlobs(): List<String> {
            val dead = refs.filterValues { it <= 0 }.keys.toList()
            for (k in dead) {
                refs.remove(k)
                blobs.remove(k)
            }
            return dead
        }

        fun getVerified(sha256Hex: String): ByteArray? {
            val key = sha256Hex.lowercase()
            val data = blobs[key] ?: return null
            val actual = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            check(actual == key) { "CAS bit-rot integrity violation for $key" }
            return data.copyOf()
        }

        fun describe(sha256Hex: String): CasObject {
            val key = sha256Hex.lowercase()
            val b = blobs[key] ?: error("Missing CAS object $key")
            val shard = "cas/${key.substring(0, 2)}/${key.substring(2, 4)}/$key"
            return CasObject(key, shard, b.size, refs[key] ?: 0)
        }
    }
}
