// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Bounded archive entry path normalizer. Strips leading `./` and `/`,
 * collapses repeated slashes, and refuses `..` traversal, NUL bytes,
 * empty paths, and paths exceeding [MAX_PATH_LEN].
 */
class ArchiveEntryPathNormalizer private constructor() {
    companion object {
        const val MAX_PATH_LEN = 1024
        const val MAX_SEGMENTS = 128

        fun normalize(rawPath: String): String {
            require(rawPath.isNotEmpty() && rawPath.length <= MAX_PATH_LEN) {
                "path length out of bounds"
            }
            require(!rawPath.contains('\u0000')) { "path contains NUL byte" }
            val parts = rawPath.split('/').filter { it.isNotEmpty() && it != "." }
            require(parts.isNotEmpty()) { "empty normalized path" }
            require(parts.size <= MAX_SEGMENTS) { "too many path segments" }
            for (part in parts) {
                require(part != "..") { "path traversal '..' refused" }
            }
            val joined = parts.joinToString("/")
            require(joined.length <= MAX_PATH_LEN) { "normalized path too long" }
            return joined
        }
    }
}
