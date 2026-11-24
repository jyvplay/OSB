// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * Bounded vault entry metadata. Records an encrypted vault entry with its
 * path, nonce, authentication tag, encryption algorithm, and key id. Does
 * not perform encryption or decryption — the crypto layer supplies the
 * nonce and tag. Metadata is bounded in count and field lengths.
 *
 * Declared narrowings: at most [MAX_ENTRIES] entries; path at most
 * [MAX_PATH_LEN] chars; nonce exactly [NONCE_LEN] bytes; tag exactly
 * [TAG_LEN] bytes; algorithm and key id bounded; only AES-256-GCM and
 * ChaCha20-Poly1305 algorithms admitted.
 */
class VaultEntry private constructor(
    private val entries: LinkedHashMap<String, VaultRecord>
) {
    companion object {
        const val MAX_ENTRIES = 4096
        const val MAX_PATH_LEN = 4096
        const val NONCE_LEN = 12  // AES-GCM nonce
        const val TAG_LEN = 16    // authentication tag
        const val MAX_ALGO_LEN = 32
        const val MAX_KEY_ID_LEN = 128
        private val ALGO = setOf("AES-256-GCM", "ChaCha20-Poly1305")

        data class VaultRecord(
            val path: String,
            val nonce: ByteArray,
            val tag: ByteArray,
            val algorithm: String,
            val keyId: String
        )

        fun empty(): VaultEntry = VaultEntry(LinkedHashMap())
    }

    fun addEntry(
        path: String,
        nonce: ByteArray,
        tag: ByteArray,
        algorithm: String,
        keyId: String
    ) {
        require(path.length <= MAX_PATH_LEN) { "path exceeds $MAX_PATH_LEN chars" }
        require(nonce.size == NONCE_LEN) { "nonce must be $NONCE_LEN bytes" }
        require(tag.size == TAG_LEN) { "tag must be $TAG_LEN bytes" }
        require(ALGO.contains(algorithm)) { "unsupported algorithm: $algorithm" }
        require(algorithm.length <= MAX_ALGO_LEN) { "algorithm too long" }
        require(keyId.length <= MAX_KEY_ID_LEN) { "keyId too long" }
        require(entries.size < MAX_ENTRIES) { "too many entries: $MAX_ENTRIES" }
        require(!entries.containsKey(path)) { "duplicate path: $path" }
        entries[path] = VaultRecord(path, nonce.copyOf(), tag.copyOf(), algorithm, keyId)
    }

    fun removeEntry(path: String): Boolean = entries.remove(path) != null

    fun entry(path: String): VaultRecord? = entries[path]
    fun contains(path: String): Boolean = entries.containsKey(path)
    fun size(): Int = entries.size
    fun clear() = entries.clear()
}
