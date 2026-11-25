// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Bounded storage checksum manifest verifier.
 */
class StorageChecksumManifest private constructor() {
    companion object {
        fun empty(): StorageChecksumManifest = StorageChecksumManifest()
    }

    private val expected = LinkedHashMap<String, String>()

    fun put(path: String, sha256: String) {
        require(path.isNotEmpty() && sha256.length == 64) { "invalid entry" }
        expected[path] = sha256.lowercase()
    }

    fun verify(path: String, actualSha256: String): Boolean =
        expected[path] == actualSha256.lowercase()
}
